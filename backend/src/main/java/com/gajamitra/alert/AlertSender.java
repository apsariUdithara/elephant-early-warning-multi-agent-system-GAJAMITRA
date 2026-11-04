package com.gajamitra.alert;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gajamitra.site.Subscriber;
import com.gajamitra.site.SubscriberRepository;
import com.gajamitra.sms.SmsGateway;

/**
 * Sends an accepted alert's SMS after its transaction commits, never inside it (no database transaction is held
 * open while the provider is called). Each delivery is saved on its own, so a crash part-way leaves the rest PENDING
 * and a retry can't text anyone twice (UNIQUE alert + subscriber). SHADOW deliveries are never PENDING, so they are
 * never sent.
 */
@Component
class AlertSender {

	record AlertStored(UUID alertId) {
	}

	private static final Logger log = LoggerFactory.getLogger(AlertSender.class);

	private final AlertRepository alerts;
	private final AlertDeliveryRepository deliveries;
	private final SubscriberRepository subscribers;
	private final SmsGateway sms;
	private final TransactionTemplate newTransaction;

	AlertSender(AlertRepository alerts, AlertDeliveryRepository deliveries, SubscriberRepository subscribers,
			SmsGateway sms, PlatformTransactionManager transactions) {
		this.alerts = alerts;
		this.deliveries = deliveries;
		this.subscribers = subscribers;
		this.sms = sms;
		// After commit the old transaction is finished: each status update needs a transaction of its own.
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onAlertStored(AlertStored event) {
		Alert alert = alerts.findById(event.alertId()).orElseThrow();
		for (AlertDelivery delivery : deliveries.findByAlertIdAndStatus(alert.id(), AlertDelivery.Status.PENDING)) {
			Subscriber to = subscribers.findById(delivery.subscriberId()).orElse(null);
			if (to == null) {
				continue; // villager deleted meanwhile: nothing to send to
			}
			try {
				delivery.sent(sms.send(to.phone(), alert.textFor(to.language())));
			}
			catch (RuntimeException e) {
				log.warn("SMS to {} for alert {} failed: {}", Subscriber.masked(to.phone()), alert.id(),
						e.getClass().getSimpleName());
				delivery.failed();
			}
			newTransaction.executeWithoutResult(status -> deliveries.save(delivery));
		}
	}

}
