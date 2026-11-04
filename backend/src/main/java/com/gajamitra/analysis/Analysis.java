package com.gajamitra.analysis;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/**
 * One uploaded clip and its analysis. Only the columns the alert tools need are mapped so far; the upload API adds
 * the rest (video, boundary, status transitions). The tracker risk is written once and never changed.
 */
@Entity
@Table(name = "analysis")
public class Analysis {

	public static final Set<String> RISKS = Set.of("low", "medium", "high", "critical");

	@Id
	private UUID id;

	@Column(name = "site_id", nullable = false, updatable = false)
	private UUID siteId;

	private String risk;

	@ColumnTransformer(write = "?::jsonb")
	private String summary;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Analysis() {
	}

	public UUID id() {
		return id;
	}

	public UUID siteId() {
		return siteId;
	}

	public Instant createdAt() {
		return createdAt;
	}

	/** The stored tracker risk, or null if record_assessment hasn't run yet. */
	public String risk() {
		return risk;
	}

	/**
	 * Store the deterministic tracker's risk (MCP record_assessment). Recording the same risk again is a no-op, so a
	 * redelivered job is harmless; a different risk is refused, because the alert re-check depends on it.
	 */
	public boolean recordRisk(String newRisk, String summaryJson) {
		if (!RISKS.contains(newRisk)) {
			throw new IllegalArgumentException("unknown risk '" + newRisk + "'");
		}
		if (risk != null) {
			return risk.equals(newRisk);
		}
		risk = newRisk;
		summary = summaryJson;
		updatedAt = Instant.now();
		return true;
	}

}
