package com.gajamitra.site;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/sites/**: ADMIN only (SecurityConfiguration). */
@RestController
@RequestMapping("/api/sites")
class SiteController {

	record SiteView(UUID id, String name, String villages, String officerContact, String mode,
			boolean requiresApproval, long approvalTimeoutS) {

		static SiteView of(Site s) {
			return new SiteView(s.id(), s.name(), s.villages(), s.officerContact(), s.mode().name(),
					s.requiresApproval(), s.approvalTimeout().toSeconds());
		}
	}

	record ModeRequest(@NotNull Site.Mode mode) {
	}

	record SubscriberRequest(@NotNull String phone, @NotNull Subscriber.Language language) {
	}

	record SubscriberView(UUID id, String phone, String language) {
	}

	private final SiteService service;
	private final SubscriberRepository subscribers;

	SiteController(SiteService service, SubscriberRepository subscribers) {
		this.service = service;
		this.subscribers = subscribers;
	}

	@GetMapping
	List<SiteView> all() {
		return service.all().stream().map(SiteView::of).toList();
	}

	@PostMapping
	ResponseEntity<SiteView> create(@Valid @RequestBody SiteForm form) {
		return ResponseEntity.status(HttpStatus.CREATED).body(SiteView.of(service.create(form)));
	}

	@PutMapping("/{id}")
	ResponseEntity<SiteView> update(@PathVariable UUID id, @Valid @RequestBody SiteForm form) {
		return ResponseEntity.of(service.update(id, form).map(SiteView::of));
	}

	@PutMapping("/{id}/mode")
	ResponseEntity<SiteView> mode(@PathVariable UUID id, @Valid @RequestBody ModeRequest request) {
		return ResponseEntity.of(service.switchMode(id, request.mode()).map(SiteView::of));
	}

	/** ADMIN sees full numbers (needed to manage them); logs and audit entries mask them. */
	@GetMapping("/{id}/subscribers")
	List<SubscriberView> subscribers(@PathVariable UUID id) {
		return subscribers.findBySiteId(id).stream()
			.map(s -> new SubscriberView(s.id(), s.phone(), s.language().name()))
			.toList();
	}

	@PostMapping("/{id}/subscribers")
	ResponseEntity<SubscriberView> addSubscriber(@PathVariable UUID id, @Valid @RequestBody SubscriberRequest request) {
		Subscriber s = service.addSubscriber(id, request.phone(), request.language());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new SubscriberView(s.id(), s.phone(), s.language().name()));
	}

	@DeleteMapping("/{id}/subscribers/{subscriberId}")
	ResponseEntity<Void> removeSubscriber(@PathVariable UUID id, @PathVariable UUID subscriberId) {
		return service.removeSubscriber(id, subscriberId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

}
