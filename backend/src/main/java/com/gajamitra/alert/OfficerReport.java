package com.gajamitra.alert;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** The report for the wildlife officer. Same idempotency rule as alerts. */
@Entity
@Immutable
@Table(name = "officer_report")
public class OfficerReport {

	@Id
	private UUID id;

	@Column(name = "analysis_id", nullable = false)
	private UUID analysisId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(nullable = false)
	private String report;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OfficerReport() {
	}

	OfficerReport(UUID analysisId, String idempotencyKey, String report) {
		this.id = UUID.randomUUID();
		this.analysisId = analysisId;
		this.idempotencyKey = idempotencyKey;
		this.report = report;
		this.createdAt = Instant.now();
	}

	public UUID id() {
		return id;
	}

}
