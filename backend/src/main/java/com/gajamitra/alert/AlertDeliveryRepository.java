package com.gajamitra.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status changes are conditional updates, each committed on its own: PENDING or FAILED -> SENDING (claim) before the
 * provider is called, then SENDING -> SENT or FAILED. Only the caller whose claim succeeded sends, so no one is texted
 * twice; a row left in SENDING means "maybe sent" and is never claimed again.
 */
interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, UUID> {

	/** Provider calls per delivery, the first one included. */
	int MAX_ATTEMPTS = 3;

	List<AlertDelivery> findByAlertIdAndStatus(UUID alertId, AlertDelivery.Status status);

	List<AlertDelivery> findByAlertId(UUID alertId);

	/** Rows of (alert id, status, how many deliveries): one query for all alerts of an analysis. */
	@Query("select d.alertId, d.status, count(d) from AlertDelivery d where d.alertId in :alertIds"
			+ " group by d.alertId, d.status")
	List<Object[]> countByAlertAndStatus(java.util.Collection<UUID> alertIds);

	@Query("select d from AlertDelivery d where d.status in (com.gajamitra.alert.AlertDelivery.Status.PENDING,"
			+ " com.gajamitra.alert.AlertDelivery.Status.FAILED) and d.subscriberId is not null and d.attempts < "
			+ MAX_ATTEMPTS
			+ " and d.updatedAt < :before order by d.updatedAt")
	List<AlertDelivery> findRetryable(Instant before, Limit limit);

	long countByStatusAndUpdatedAtBefore(AlertDelivery.Status status, Instant before);

	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query("update AlertDelivery d set d.status = com.gajamitra.alert.AlertDelivery.Status.SENDING,"
			+ " d.attempts = d.attempts + 1, d.updatedAt = CURRENT_TIMESTAMP where d.id = :id"
			+ " and d.subscriberId is not null and d.attempts < "
			+ MAX_ATTEMPTS + " and d.status in (com.gajamitra.alert.AlertDelivery.Status.PENDING,"
			+ " com.gajamitra.alert.AlertDelivery.Status.FAILED)")
	int claim(UUID id);

	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query("update AlertDelivery d set d.status = com.gajamitra.alert.AlertDelivery.Status.SENT, d.providerId = :providerId,"
			+ " d.updatedAt = CURRENT_TIMESTAMP where d.id = :id and d.status = com.gajamitra.alert.AlertDelivery.Status.SENDING")
	int markSent(UUID id, String providerId);

	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query("update AlertDelivery d set d.status = com.gajamitra.alert.AlertDelivery.Status.FAILED, d.updatedAt = CURRENT_TIMESTAMP"
			+ " where d.id = :id and d.status = com.gajamitra.alert.AlertDelivery.Status.SENDING")
	int markFailed(UUID id);

}
