package com.gajamitra.alert;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gajamitra.site.Subscriber;
import com.gajamitra.site.SubscriberRepository;
import com.gajamitra.sms.SmsGateway;

/**
 * Sends an accepted alert's SMS after its transaction commits, never inside it (no database transaction is held
 * open while the provider is called). Each delivery is claimed (PENDING -> SENDING) before the call and finished
 * (SENT or FAILED) after it, each step committed on its own, so a crash or a failed save leaves SENDING ("maybe sent")
 * rather than PENDING, and nothing is ever sent twice. One bad delivery doesn't stop the others. SHADOW deliveries are
 * never PENDING, so they are never sent.
 */
@Component
class AlertSender {

	record AlertStored(UUID analysisId, UUID alertId) {
	}

	private static final Logger log = LoggerFactory.getLogger(AlertSender.class);

	private final AlertRepository alerts;
	private final AlertDeliveryRepository deliveries;
	private final SubscriberRepository subscribers;
	private final SmsGateway sms;

	AlertSender(AlertRepository alerts, AlertDeliveryRepository deliveries, SubscriberRepository subscribers,
			SmsGateway sms) {
		this.alerts = alerts;
		this.deliveries = deliveries;
		this.subscribers = subscribers;
		this.sms = sms;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onAlertStored(AlertStored event) {
		try (var ignored = MDC.putCloseable("analysisId", event.analysisId().toString())) {
			Alert alert = alerts.findById(event.alertId()).orElseThrow();
			for (AlertDelivery delivery : deliveries.findByAlertIdAndStatus(alert.id(), AlertDelivery.Status.PENDING)) {
				deliver(alert, delivery);
			}
		}
		catch (RuntimeException e) {
			// The tool call already answered "accepted": make the stuck deliveries loud, never silent (invariant 10).
			log.error("SMS sending for alert {} stopped; its PENDING deliveries need the retry job", event.alertId(), e);
		}
	}

	/** Claim, send and record one delivery. Used after commit and by {@link DeliveryRetryJob}. */
	void deliver(Alert alert, AlertDelivery delivery) {
		try {
			Subscriber to = subscribers.findById(delivery.subscriberId()).orElse(null);
			if (to == null || deliveries.claim(delivery.id()) == 0) {
				return; // villager deleted meanwhile, or another sender claimed it
			}
			try {
				deliveries.markSent(delivery.id(), sms.send(to.phone(), alert.textFor(to.language())));
			}
			catch (RuntimeException e) {
				log.warn("SMS to {} for alert {} failed: {}", Subscriber.masked(to.phone()), alert.id(),
						e.getClass().getSimpleName());
				deliveries.markFailed(delivery.id());
			}
		}
		catch (RuntimeException e) {
			log.error("delivery {} of alert {} is stuck (PENDING or SENDING)", delivery.id(), alert.id(), e);
		}
	}

}
