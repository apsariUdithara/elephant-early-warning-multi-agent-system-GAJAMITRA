package com.gajamitra.site;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
/** A monitored boundary. Created and changed only by an ADMIN through {@link SiteService}. */
@Entity
@Table(name = "site")
public class Site {

	public enum Mode { SHADOW, LIVE }

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String villages;

	@Column(name = "officer_contact", nullable = false)
	private String officerContact;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Mode mode;

	@Column(name = "requires_approval", nullable = false)
	private boolean requiresApproval;

	@Column(name = "approval_timeout_s", nullable = false)
	private int approvalTimeoutS;

	protected Site() {
	}

	/** A new site starts in SHADOW mode: alerts are stored and logged, no SMS is sent until an ADMIN switches it. */
	static Site create(SiteForm form) {
		var site = new Site();
		site.id = UUID.randomUUID();
		site.mode = Mode.SHADOW;
		site.update(form);
		return site;
	}

	void update(SiteForm form) {
		name = form.name().strip();
		villages = form.villages() == null ? "" : form.villages().strip();
		officerContact = form.officerContact() == null ? "" : form.officerContact().strip();
		requiresApproval = form.requiresApproval();
		approvalTimeoutS = form.approvalTimeoutS();
	}

	void switchTo(Mode next) {
		mode = next;
	}

	public Mode mode() {
		return mode;
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String villages() {
		return villages;
	}

	public String officerContact() {
		return officerContact;
	}

	/** In SHADOW mode alerts are stored and logged but no SMS is sent. */
	public boolean shadow() {
		return mode == Mode.SHADOW;
	}

	public boolean requiresApproval() {
		return requiresApproval;
	}

	/** How long an escalation waits for the officer before it is sent anyway (invariant 9). */
	public java.time.Duration approvalTimeout() {
		return java.time.Duration.ofSeconds(approvalTimeoutS);
	}

}
