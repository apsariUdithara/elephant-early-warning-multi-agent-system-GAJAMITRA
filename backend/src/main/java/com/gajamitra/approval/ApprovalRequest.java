package com.gajamitra.approval;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Read-only view of an officer approval. Deciding approvals (and the timeout job) come with the approvals API. */
@Entity
@Immutable
@Table(name = "approval_request")
public class ApprovalRequest {

	@Id
	private UUID id;

	@Column(name = "analysis_id", nullable = false)
	private UUID analysisId;

	@Column(nullable = false)
	private String status;

	protected ApprovalRequest() {
	}

	public String status() {
		return status;
	}

}
