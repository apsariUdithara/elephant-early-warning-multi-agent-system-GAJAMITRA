package com.gajamitra.alert;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import com.gajamitra.site.Subscriber;

/** A village alert that passed the re-check. Written once; its idempotency key is UNIQUE (invariant 4). */
@Entity
@Immutable
@Table(name = "alert")
public class Alert {

	public enum Stage { NOW, ESCALATION }

	@Id
	private UUID id;

	@Column(name = "analysis_id", nullable = false)
	private UUID analysisId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Stage stage;

	@Column(nullable = false)
	private String severity;

	@Column(name = "text_en", nullable = false)
	private String textEn;

	@Column(name = "text_si", nullable = false)
	private String textSi;

	@Column(name = "text_ta", nullable = false)
	private String textTa;

	@Column(nullable = false)
	private boolean shadow;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Alert() {
	}

	Alert(UUID analysisId, String idempotencyKey, Stage stage, VillageAlert alert, boolean shadow) {
		this.id = UUID.randomUUID();
		this.analysisId = analysisId;
		this.idempotencyKey = idempotencyKey;
		this.stage = stage;
		this.severity = alert.severity();
		this.textEn = alert.en();
		this.textSi = alert.si();
		this.textTa = alert.ta();
		this.shadow = shadow;
		this.createdAt = Instant.now();
	}

	public UUID id() {
		return id;
	}

	public Stage stage() {
		return stage;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public String severity() {
		return severity;
	}

	public boolean shadow() {
		return shadow;
	}

	/** The text in the villager's own language. */
	public String textFor(Subscriber.Language language) {
		return switch (language) {
			case EN -> textEn;
			case SI -> textSi;
			case TA -> textTa;
		};
	}

}
