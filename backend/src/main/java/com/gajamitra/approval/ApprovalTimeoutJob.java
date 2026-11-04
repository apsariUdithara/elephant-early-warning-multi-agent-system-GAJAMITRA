package com.gajamitra.approval;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 10 s, times out expired approvals (invariant 9). A separate bean on purpose: calling the service's
 * {@code @Transactional} method from inside the service would bypass the transaction proxy, and the resume published
 * after commit would never be sent.
 */
@Component
public class ApprovalTimeoutJob {

	private final ApprovalService approvals;

	private final Clock clock;

	ApprovalTimeoutJob(ApprovalService approvals, Clock clock) {
		this.approvals = approvals;
		this.clock = clock;
	}

	@Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
	public void run() {
		approvals.timeOutExpired(clock.instant());
	}

}
