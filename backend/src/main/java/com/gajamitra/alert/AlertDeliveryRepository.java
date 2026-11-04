package com.gajamitra.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, UUID> {

	List<AlertDelivery> findByAlertIdAndStatus(UUID alertId, AlertDelivery.Status status);

	List<AlertDelivery> findByAlertId(UUID alertId);

}
