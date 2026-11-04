package com.gajamitra.approval;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

	/** An escalation may go out once the officer approved it or the approval timed out (invariants 2 and 9). */
	@Query("select count(a) > 0 from ApprovalRequest a where a.analysisId = :analysisId and a.status in ('APPROVED', 'TIMED_OUT')")
	boolean escalationReleased(UUID analysisId);

}
