package com.gajamitra.approval;

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

import com.gajamitra.auth.AppUserRepository;

/** The officer's approval queue (plan Phase 3, REST API). OFFICER or ADMIN. */
@RestController
@RequestMapping("/api/approvals")
class ApprovalController {

	record ApprovalView(UUID id, UUID analysisId, String status, Instant expiresAt) {
	}

	record DecisionRequest(@NotNull ApprovalService.Decision decision, @Size(max = 500) String note) {
	}

	private final ApprovalService service;
	private final ApprovalRequestRepository approvals;
	private final AppUserRepository users;

	ApprovalController(ApprovalService service, ApprovalRequestRepository approvals, AppUserRepository users) {
		this.service = service;
		this.approvals = approvals;
		this.users = users;
	}

	@GetMapping
	List<ApprovalView> pending() {
		return approvals.findByStatusOrderByExpiresAt("PENDING", Limit.of(100))
			.stream()
			.map(a -> new ApprovalView(a.id(), a.analysisId(), a.status(), a.expiresAt()))
			.toList();
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
