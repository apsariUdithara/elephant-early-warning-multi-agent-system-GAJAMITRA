package com.gajamitra.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gajamitra.BadRequest;
import com.gajamitra.site.SiteRepository;

/** Upload a clip for analysis and read its state (plan Phase 3, REST API). OFFICER or ADMIN. */
@RestController
@RequestMapping("/api/analyses")
class AnalysisController {

	/** What the dashboard sees of an analysis: no storage paths. */
	record AnalysisView(UUID id, UUID siteId, String status, String risk, Instant createdAt) {
	}

	private final AnalysisService service;
	private final AnalysisRepository analyses;
	private final SiteRepository sites;
	private final VideoStorage storage;

	AnalysisController(AnalysisService service, AnalysisRepository analyses, SiteRepository sites,
			VideoStorage storage) {
		this.service = service;
		this.analyses = analyses;
		this.sites = sites;
		this.storage = storage;
	}

	/** 202 with the id: the analysis is QUEUED and the AI service is asked after commit. */
	@PostMapping
	ResponseEntity<Map<String, UUID>> upload(@RequestParam("video") MultipartFile video,
			@RequestParam("siteId") UUID siteId, @RequestParam("boundary") String boundary) throws IOException {
		List<Double> numbers;
		try {
			numbers = Arrays.stream(boundary.split(",")).map(String::trim).map(Double::valueOf).toList();
		}
		catch (NumberFormatException e) {
			throw new BadRequest("boundary must be six numbers between 0 and 1");
		}
		if (numbers.size() != 6 || numbers.stream().anyMatch(v -> v.isNaN() || v < 0 || v > 1)) {
			throw new BadRequest("boundary must be six numbers between 0 and 1");
		}
		if (!sites.existsById(siteId)) {
			throw new BadRequest("unknown site");
		}
		Path stored = storage.store(video);
		try {
			UUID id = service.queue(siteId, stored.toString(), numbers);
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", id));
		}
		catch (RuntimeException e) {
			Files.deleteIfExists(stored); // no analysis points at it: don't leave footage behind
			throw e;
		}
	}

	@GetMapping("/{id}")
	ResponseEntity<AnalysisView> get(@PathVariable UUID id) {
		return analyses.findById(id)
			.map(a -> ResponseEntity.ok(new AnalysisView(a.id(), a.siteId(), a.status().name(), a.risk(), a.createdAt())))
			.orElse(ResponseEntity.notFound().build());
	}

}
