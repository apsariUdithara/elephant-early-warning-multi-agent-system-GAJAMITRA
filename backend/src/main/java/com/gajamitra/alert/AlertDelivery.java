package com.gajamitra.alert;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One SMS of one alert to one villager. UNIQUE (alert, subscriber) means a retry can never text the same person
 * twice for the same alert (invariant 4). SENT means the provider accepted the message, not that the phone got it.
 * Status changes only through the conditional updates in {@link AlertDeliveryRepository}.
 */
@Entity
@Table(name = "alert_delivery")
public class AlertDelivery {

	public enum Status { PENDING, SENDING, SENT, FAILED, SKIPPED_SHADOW }

	@Id
	private UUID id;

	@Column(name = "alert_id", nullable = false, updatable = false)
	private UUID alertId;

	@Column(name = "subscriber_id", updatable = false)
	private UUID subscriberId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "provider_id")
	private String providerId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AlertDelivery() {
	}

	AlertDelivery(UUID alertId, UUID subscriberId, boolean shadow) {
		this.id = UUID.randomUUID();
		this.alertId = alertId;
		this.subscriberId = subscriberId;
		this.status = shadow ? Status.SKIPPED_SHADOW : Status.PENDING;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	public UUID subscriberId() {
		return subscriberId;
	}

	public Status status() {
		return status;
	}

	public UUID id() {
		return id;
	}

}
