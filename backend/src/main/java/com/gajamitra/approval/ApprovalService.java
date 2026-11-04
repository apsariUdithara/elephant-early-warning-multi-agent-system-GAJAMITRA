package com.gajamitra.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gajamitra.audit.AuditEntry.Actor;
import com.gajamitra.audit.AuditLog;
import com.gajamitra.messaging.Messages;
import com.gajamitra.messaging.Messages.Resume;

/**
 * Officer approvals of escalations (plan Phase 3, REST API / approval timeout). Spring owns time: when an approval
 * expires it is TIMED_OUT and the escalation is released (invariant 9: the officer is not a single point of
 * failure). Each outcome publishes analysis.resume after commit; exactly one outcome per approval, because both
 * paths are conditional updates on PENDING.
 */
@Service
public class ApprovalService {

	/** Approvals handled per timeout run. */
	static final int BATCH = 100;

	private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

	private final ApprovalRequestRepository approvals;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	ApprovalService(ApprovalRequestRepository approvals, AuditLog audit, ApplicationEventPublisher events, Clock clock) {
		this.approvals = approvals;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	public enum Decision { APPROVE, REJECT }

	/** The worker reported approval_required. Only one approval per analysis, so a redelivered event adds nothing. */
	@Transactional
	public void open(UUID analysisId, String proposedAlertJson, java.time.Duration timeout) {
		if (approvals.existsByAnalysisId(analysisId)) {
			return;
		}
		Instant now = clock.instant();
		approvals.save(new ApprovalRequest(analysisId, proposedAlertJson, now, now.plus(timeout)));
		audit.record(analysisId, Actor.SYSTEM, "approval_opened", Map.of("expires_in_s", timeout.toSeconds()));
	}

	/** The officer's decision. False if it was already decided or has expired (the timeout already released it). */
	@Transactional
	public boolean decide(UUID approvalId, UUID officer, Decision decision, String note) {
		var approval = approvals.findById(approvalId).orElse(null);
		if (approval == null) {
			return false;
		}
		String status = decision == Decision.APPROVE ? "APPROVED" : "REJECTED";
		if (approvals.decide(approvalId, status, officer, note, clock.instant()) == 0) {
			return false;
		}
		audit.record(approval.analysisId(), Actor.OFFICER, "approval_" + status.toLowerCase(),
				Map.of("officer", officer.toString()));
		events.publishEvent(resume(approval.analysisId(), decision == Decision.APPROVE ? "approve" : "reject",
				officer.toString(), note));
		return true;
	}

	/** Expired approvals become TIMED_OUT and their escalations are released. Run by {@link ApprovalTimeoutJob}. */
	@Transactional
	public int timeOutExpired(Instant now) {
		int timedOut = 0;
		for (var approval : approvals.findByStatusAndExpiresAtLessThanEqual("PENDING", now, Limit.of(BATCH))) {
			if (approvals.timeOut(approval.id(), now) == 1) {
				audit.record(approval.analysisId(), Actor.SYSTEM, "approval_timed_out", Map.of());
				events.publishEvent(resume(approval.analysisId(), "timeout", null, null));
				log.info("approval for analysis {} timed out: the escalation is released", approval.analysisId());
				timedOut++;
			}
		}
		return timedOut;
	}

	static Resume resume(UUID analysisId, String decision, String officer, String note) {
		return new Resume(Messages.SCHEMA_VERSION, analysisId.toString(), decision, officer, note);
	}

}
