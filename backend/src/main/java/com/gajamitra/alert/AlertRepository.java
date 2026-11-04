package com.gajamitra.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AlertRepository extends JpaRepository<Alert, UUID> {

	Optional<Alert> findByIdempotencyKey(String idempotencyKey);

	List<Alert> findByAnalysisIdOrderByCreatedAt(UUID analysisId);

}
