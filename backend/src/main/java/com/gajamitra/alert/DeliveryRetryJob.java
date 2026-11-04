package com.gajamitra.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes deliveries the after-commit sender didn't (invariant 10): a crash between commit and sending leaves them
 * PENDING, a provider outage leaves them FAILED. Each is claimed exactly like the first send, so a retry never texts
 * anyone twice, and stops after {@link AlertDeliveryRepository#MAX_ATTEMPTS}. Rows stuck in SENDING may already have
 * reached the phone, so they are never resent: they are reported at ERROR for a person to check.
 */
@Component
public class DeliveryRetryJob {

	/** A delivery this long without progress is retried (gives the after-commit sender time to finish first). */
	static final Duration RETRY_AFTER = Duration.ofSeconds(60);

	/** SENDING for this long means the process died mid-send or the status save failed. */
	static final Duration STUCK_AFTER = Duration.ofMinutes(5);

	/** Provider calls per delivery in all; a FAILED delivery with fewer is still due to be retried. */
	public static final int MAX_ATTEMPTS = AlertDeliveryRepository.MAX_ATTEMPTS;

	/** Deliveries handled per run, so one run stays short. */
	static final int BATCH = 100;

	private static final Logger log = LoggerFactory.getLogger(DeliveryRetryJob.class);

	private final AlertDeliveryRepository deliveries;
	private final AlertRepository alerts;
	private final AlertSender sender;
	private final Clock clock;

	DeliveryRetryJob(AlertDeliveryRepository deliveries, AlertRepository alerts, AlertSender sender, Clock clock) {
		this.deliveries = deliveries;
		this.alerts = alerts;
		this.sender = sender;
		this.clock = clock;
	}

	@Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
	void scheduled() {
		run(clock.instant());
	}

	/** One pass: retry what is due at {@code now}, report what is stuck. Returns how many deliveries were retried. */
	public int run(Instant now) {
		int retried = 0;
		for (AlertDelivery delivery : deliveries.findRetryable(now.minus(RETRY_AFTER), Limit.of(BATCH))) {
			alerts.findById(delivery.alertId()).ifPresent(alert -> sender.deliver(alert, delivery));
			retried++;
		}
		long stuck = deliveries.countByStatusAndUpdatedAtBefore(AlertDelivery.Status.SENDING, now.minus(STUCK_AFTER));
		if (stuck > 0) {
			log.error("{} SMS deliveries are stuck in SENDING (maybe sent); check them by hand, they won't be resent",
					stuck);
		}
		return retried;
	}

}
