package com.gajamitra.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status changes are conditional updates, each committed on its own: PENDING -> SENDING (claim) before the provider is
 * called, then SENDING -> SENT or FAILED. Only the caller whose claim succeeded sends, so no one is texted twice.
 */
interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, UUID> {

	List<AlertDelivery> findByAlertIdAndStatus(UUID alertId, AlertDelivery.Status status);

	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query("update AlertDelivery d set d.status = com.gajamitra.alert.AlertDelivery.Status.SENDING, d.updatedAt = CURRENT_TIMESTAMP"
			+ " where d.id = :id and d.status = com.gajamitra.alert.AlertDelivery.Status.PENDING")
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
