package com.gajamitra.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

/** One line of the audit trail (invariant 11). Written once, never changed. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEntry {

	public enum Actor { AGENT, OFFICER, SYSTEM }

	@Id
	private UUID id;

	@Column(name = "analysis_id")
	private UUID analysisId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Actor actor;

	@Column(nullable = false)
	private String action;

	@Column(nullable = false)
	@ColumnTransformer(write = "?::jsonb")
	private String payload;

	@Column(nullable = false)
	private Instant at;

	protected AuditEntry() {
	}

	AuditEntry(UUID analysisId, Actor actor, String action, String payloadJson) {
		this.id = UUID.randomUUID();
		this.analysisId = analysisId;
		this.actor = actor;
		this.action = action;
		this.payload = payloadJson;
		this.at = Instant.now();
	}

	public Actor actor() {
		return actor;
	}

	public String action() {
		return action;
	}

}
