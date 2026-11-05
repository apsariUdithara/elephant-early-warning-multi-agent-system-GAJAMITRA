package com.gajamitra.site;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The sites an officer can start an analysis for: id, name and mode only. Officer contact details, villages, approval
 * settings and villagers' phone numbers stay with the admin endpoints ({@code /api/sites}). OFFICER or ADMIN.
 */
@RestController
@RequestMapping("/api/site-options")
class SiteOptionsController {

	record Option(UUID id, String name, String mode) {
	}

	private final SiteService service;

	SiteOptionsController(SiteService service) {
		this.service = service;
	}

	@GetMapping
	List<Option> options() {
		return service.all().stream().map(s -> new Option(s.id(), s.name(), s.mode().name())).toList();
	}

}
