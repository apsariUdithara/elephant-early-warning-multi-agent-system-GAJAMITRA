package com.gajamitra.site;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A monitored boundary. Read-only here; site administration comes with the sites API. */
@Entity
@Immutable
@Table(name = "site")
public class Site {

	public enum Mode { SHADOW, LIVE }

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Mode mode;

	@Column(name = "requires_approval", nullable = false)
	private boolean requiresApproval;

	protected Site() {
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	/** In SHADOW mode alerts are stored and logged but no SMS is sent. */
	public boolean shadow() {
		return mode == Mode.SHADOW;
	}

	public boolean requiresApproval() {
		return requiresApproval;
	}

}
