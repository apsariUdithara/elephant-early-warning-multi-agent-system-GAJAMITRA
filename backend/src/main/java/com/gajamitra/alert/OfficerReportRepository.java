package com.gajamitra.alert;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OfficerReportRepository extends JpaRepository<OfficerReport, UUID> {

	Optional<OfficerReport> findByIdempotencyKey(String idempotencyKey);

	Optional<OfficerReport> findByAnalysisId(UUID analysisId);

}
