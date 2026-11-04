package com.gajamitra.approval;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/**
 * An escalation waiting for an officer. Decisions are conditional updates in {@link ApprovalRequestRepository}
 * ("... where status = 'PENDING'"), so an officer's click racing the timeout produces exactly one outcome.
 */
@Entity
@Table(name = "approval_request")
public class ApprovalRequest {

	@Id
	private UUID id;

	@Column(name = "analysis_id", nullable = false, updatable = false)
	private UUID analysisId;

	@Column(name = "proposed_alert", nullable = false, updatable = false)
	@ColumnTransformer(write = "?::jsonb")
	private String proposedAlert;

	@Column(nullable = false)
	private String status;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "decided_by")
	private UUID decidedBy;

	private String note;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ApprovalRequest() {
	}

	ApprovalRequest(UUID analysisId, String proposedAlertJson, Instant now, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.analysisId = analysisId;
		this.proposedAlert = proposedAlertJson;
		this.status = "PENDING";
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	public UUID id() {
		return id;
	}

	public UUID analysisId() {
		return analysisId;
	}

	/** The escalation the officer is asked to approve, as the AI service proposed it (JSON text). */
	public String proposedAlert() {
		return proposedAlert;
	}

	public String status() {
		return status;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public UUID decidedBy() {
		return decidedBy;
	}

	public String note() {
		return note;
	}

}
