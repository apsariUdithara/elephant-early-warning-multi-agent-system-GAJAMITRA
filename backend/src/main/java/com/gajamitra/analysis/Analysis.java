package com.gajamitra.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One uploaded clip and its analysis. Status changes only through the methods below, which never move a finished
 * analysis backwards: events arrive at least once and possibly out of order (invariant 5). The tracker risk is
 * written once and never changed.
 */
@Entity
@Table(name = "analysis")
public class Analysis {

	public static final Set<String> RISKS = Set.of("low", "medium", "high", "critical");

	public enum Status {
		QUEUED, RUNNING, AWAITING_APPROVAL, COMPLETED, FAILED;

		boolean finished() {
			return this == COMPLETED || this == FAILED;
		}
	}

	@Id
	private UUID id;

	@Column(name = "site_id", nullable = false, updatable = false)
	private UUID siteId;

	@Column(name = "video_path")
	private String videoPath;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, updatable = false, columnDefinition = "double precision[]")
	private double[] boundary;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	private String risk;

	@ColumnTransformer(write = "?::jsonb")
	private String summary;

	@ColumnTransformer(write = "?::jsonb")
	private String result;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Analysis() {
	}

	/** A new analysis waiting for the AI service. The boundary is six numbers between 0 and 1. */
	public static Analysis queued(UUID siteId, String videoPath, List<Double> boundary) {
		if (boundary.size() != 6 || boundary.stream().anyMatch(v -> v == null || v < 0 || v > 1)) {
			throw new IllegalArgumentException("boundary must be six numbers between 0 and 1");
		}
		var analysis = new Analysis();
		analysis.id = UUID.randomUUID();
		analysis.siteId = siteId;
		analysis.videoPath = videoPath;
		analysis.boundary = boundary.stream().mapToDouble(Double::doubleValue).toArray();
		analysis.status = Status.QUEUED;
		analysis.updatedAt = Instant.now();
		return analysis;
	}

	public UUID id() {
		return id;
	}

	public UUID siteId() {
		return siteId;
	}

	public String videoPath() {
		return videoPath;
	}

	public List<Double> boundary() {
		return java.util.Arrays.stream(boundary).boxed().toList();
	}

	public Status status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public String result() {
		return result;
	}

	public String failureReason() {
		return failureReason;
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
		touch();
		return true;
	}

	/** The worker started (or resumed) the graph. Ignored once finished or while an approval is pending. */
	public boolean markRunning() {
		return moveTo(Status.RUNNING, status == Status.QUEUED);
	}

	/** The graph stopped at the approval gate. */
	public boolean awaitApproval() {
		return moveTo(Status.AWAITING_APPROVAL, !status.finished());
	}

	public boolean complete(String resultJson) {
		if (!moveTo(Status.COMPLETED, !status.finished())) {
			return false;
		}
		result = resultJson;
		return true;
	}

	public boolean fail(String reason) {
		if (!moveTo(Status.FAILED, !status.finished())) {
			return false;
		}
		failureReason = reason;
		return true;
	}

	private boolean moveTo(Status next, boolean allowed) {
		if (!allowed || status == next) {
			return false;
		}
		status = next;
		touch();
		return true;
	}

	private void touch() {
		updatedAt = Instant.now();
	}

}
