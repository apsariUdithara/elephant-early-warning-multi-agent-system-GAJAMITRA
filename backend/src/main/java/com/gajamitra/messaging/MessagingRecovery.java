package com.gajamitra.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gajamitra.analysis.Analysis;
import com.gajamitra.analysis.AnalysisRepository;
import com.gajamitra.approval.ApprovalRequestRepository;
import com.gajamitra.messaging.Messages.AnalysisRequest;

/**
 * Republishes what an after-commit publish may have lost (broker down at that moment). Duplicates are harmless: the
 * worker continues from its checkpoint (invariant 6) and ignores a resume for a run that isn't waiting.
 * <ul>
 * <li>analyses still QUEUED after {@link #STALE_AFTER} → analysis.requests again (plan Phase 3, REST API);</li>
 * <li>approvals decided or timed out over {@link #STALE_AFTER} ago whose analysis is still AWAITING_APPROVAL →
 * analysis.resume again, so a lost message can't hold back an escalation forever (invariant 9).</li>
 * </ul>
 */
@Component
public class MessagingRecovery {

	/** A decided approval whose analysis is still waiting after this long gets its resume again. */
	static final Duration STALE_AFTER = Duration.ofMinutes(2);

	/** A request still QUEUED after this long is sent again (long enough not to duplicate a normal backlog). */
	static final Duration QUEUED_STALE_AFTER = Duration.ofMinutes(10);

	/** Longer than the longest SAM call plus the models: an analysis RUNNING this long has a dead worker. */
	static final Duration STUCK_AFTER = Duration.ofMinutes(45);

	static final int BATCH = 100;

	private static final Logger log = LoggerFactory.getLogger(MessagingRecovery.class);

	private final AnalysisRepository analyses;
	private final ApprovalRequestRepository approvals;
	private final Publisher publisher;
	private final Clock clock;

	MessagingRecovery(AnalysisRepository analyses, ApprovalRequestRepository approvals, Publisher publisher,
			Clock clock) {
		this.analyses = analyses;
		this.approvals = approvals;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
	void scheduled() {
		run(clock.instant());
	}

	/** One pass at {@code now}. Returns how many messages were republished. */
	public int run(Instant now) {
		Instant before = now.minus(STALE_AFTER);
		int sent = 0;
		try {
			for (Analysis a : analyses.findByStatusAndCreatedAtBefore(Analysis.Status.QUEUED,
					now.minus(QUEUED_STALE_AFTER), Limit.of(BATCH))) {
				publisher.send(MessagingConfiguration.REQUESTS, request(a));
				sent++;
			}
			for (var approval : approvals.decidedButNotResumed(before, Limit.of(BATCH))) {
				String decision = switch (approval.status()) {
					case "APPROVED" -> "approve";
					case "REJECTED" -> "reject";
					default -> "timeout";
				};
				publisher.send(MessagingConfiguration.RESUME,
						new Messages.Resume(Messages.SCHEMA_VERSION, approval.analysisId().toString(), decision,
								approval.decidedBy() == null ? null : approval.decidedBy().toString(), approval.note()));
				sent++;
			}
		}
		catch (AmqpException e) {
			log.error("can't reach RabbitMQ to republish; analyses and escalations are waiting", e);
		}
		// Nothing can resend these: a worker died without a failed event, or the message is in analysis.dlq.
		// Loud, never silent (invariant 10); a person checks them.
		long stuck = analyses.countByStatusInAndUpdatedAtBefore(
				java.util.List.of(Analysis.Status.RUNNING, Analysis.Status.AWAITING_APPROVAL), now.minus(STUCK_AFTER));
		if (stuck > 0) {
			log.error("{} analyses have made no progress for {} minutes (RUNNING or AWAITING_APPROVAL); check the "
					+ "worker and analysis.dlq", stuck, STUCK_AFTER.toMinutes());
		}
		return sent;
	}

	public static AnalysisRequest request(Analysis analysis) {
		return new AnalysisRequest(Messages.SCHEMA_VERSION, analysis.id().toString(), analysis.siteId().toString(),
				analysis.videoPath(), analysis.boundary());
	}

}
