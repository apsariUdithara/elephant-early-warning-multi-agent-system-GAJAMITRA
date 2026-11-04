package com.gajamitra.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.json.JsonMapper;

import com.gajamitra.auth.AppUserRepository;
import com.gajamitra.evidence.EvidenceStore;

/** The officer's approval queue (plan Phase 3, REST API). OFFICER or ADMIN. */
@RestController
@RequestMapping("/api/approvals")
class ApprovalController {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApprovalController.class);

	/**
	 * proposedAlerts: what would be sent if approved (severity and en, si, ta text); untrusted AI text. serverNow is
	 * the server's clock when the list was read: the page counts down to expiresAt (also server time) with it, so a
	 * wrong clock in the browser can't make the escalation look sent, or held, when it is not.
	 */
	record ApprovalView(UUID id, UUID analysisId, String status, Instant expiresAt, Object proposedAlerts,
			Instant serverNow, int frames) {
	}

	record DecisionRequest(@NotNull ApprovalService.Decision decision, @Size(max = 500) String note) {
	}

	private final ApprovalService service;
	private final ApprovalRequestRepository approvals;
	private final AppUserRepository users;
	private final JsonMapper json;
	private final Clock clock;
	private final EvidenceStore evidence;

	ApprovalController(ApprovalService service, ApprovalRequestRepository approvals, AppUserRepository users,
			JsonMapper json, Clock clock, EvidenceStore evidence) {
		this.evidence = evidence;
		this.json = json;
		this.clock = clock;
		this.service = service;
		this.approvals = approvals;
		this.users = users;
	}

	@GetMapping
	List<ApprovalView> pending() {
		Instant now = clock.instant();
		return approvals.findByStatusOrderByExpiresAt("PENDING", Limit.of(100))
			.stream()
			.map(a -> new ApprovalView(a.id(), a.analysisId(), a.status(), a.expiresAt(), alerts(a), now,
					evidence.count(a.analysisId())))
			.toList();
	}

	/** The proposed alerts as JSON; an unreadable one is null, and the page then says it cannot show the text. */
	private Object alerts(ApprovalRequest approval) {
		try {
			String text = approval.proposedAlert();
			return text == null || text.isBlank() ? null : json.readValue(text, Object.class);
		}
		catch (RuntimeException e) { // never let one unreadable approval hide all the others
			log.warn("proposed alert of approval {} could not be read", approval.id());
			return null;
		}
	}

	/** 204 when decided; 409 when it was already decided or has timed out (the escalation is then already released). */
	@PostMapping("/{id}")
	ResponseEntity<?> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request,
			@AuthenticationPrincipal UserDetails officer) {
		var user = users.findByUsername(officer.getUsername());
		if (user.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // deleted while logged in
		}
		UUID officerId = user.get().id();
		if (service.decide(id, officerId, request.decision(), request.note())) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "already decided, timed out or unknown"));
	}

}
