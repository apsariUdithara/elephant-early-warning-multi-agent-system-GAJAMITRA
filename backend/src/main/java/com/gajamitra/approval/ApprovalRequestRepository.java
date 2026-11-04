package com.gajamitra.approval;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

	/** An escalation may go out once the officer approved it or the approval timed out (invariants 2 and 9). */
	@Query("select count(a) > 0 from ApprovalRequest a where a.analysisId = :analysisId and a.status in ('APPROVED', 'TIMED_OUT')")
	boolean escalationReleased(UUID analysisId);

	boolean existsByAnalysisId(UUID analysisId);

	List<ApprovalRequest> findByStatusAndExpiresAtLessThanEqual(String status, Instant now, Limit limit);

	/** The officer's decision; only while still PENDING and not yet expired (the timeout wins a tie). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ApprovalRequest a set a.status = :status, a.decidedBy = :officer, a.note = :note,"
			+ " a.decidedAt = :now where a.id = :id and a.status = 'PENDING' and a.expiresAt > :now")
	int decide(UUID id, String status, UUID officer, String note, Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ApprovalRequest a set a.status = 'TIMED_OUT', a.decidedAt = :now where a.id = :id"
			+ " and a.status = 'PENDING'")
	int timeOut(UUID id, Instant now);

	/** Decided before {@code before}, but the analysis is still waiting: the resume message may have been lost. */
	@Query("select a from ApprovalRequest a, com.gajamitra.analysis.Analysis x where x.id = a.analysisId"
			+ " and x.status = com.gajamitra.analysis.Analysis.Status.AWAITING_APPROVAL"
			+ " and a.status <> 'PENDING' and a.decidedAt < :before")
	List<ApprovalRequest> decidedButNotResumed(Instant before, Limit limit);

}
