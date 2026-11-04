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

import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.gajamitra.BadRequest;
import com.gajamitra.alert.AlertQuery;
import com.gajamitra.site.SiteRepository;

/** Upload a clip for analysis and read its state (plan Phase 3, REST API). OFFICER or ADMIN. */
@RestController
@RequestMapping("/api/analyses")
class AnalysisController {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalysisController.class);

	/** One line of the history list: no storage paths. */
	record AnalysisView(UUID id, UUID siteId, String status, String risk, Instant createdAt) {

		static AnalysisView of(Analysis a) {
			return new AnalysisView(a.id(), a.siteId(), a.status().name(), a.risk(), a.createdAt());
		}
	}

	/** Everything the live page shows: the run's outcome, what was sent and the officer report. */
	record AnalysisDetail(UUID id, UUID siteId, String status, String risk, Instant createdAt, String failureReason,
			Map<String, Object> result, List<AlertQuery.AlertView> alerts, String officerReport) {
	}

	private final AnalysisService service;
	private final AnalysisRepository analyses;
	private final SiteRepository sites;
	private final VideoStorage storage;
	private final AlertQuery alertQuery;
	private final AnalysisEvents liveEvents;
	private final JsonMapper json;

	AnalysisController(AnalysisService service, AnalysisRepository analyses, SiteRepository sites,
			VideoStorage storage, AlertQuery alertQuery, AnalysisEvents liveEvents, JsonMapper json) {
		this.json = json;
		this.service = service;
		this.analyses = analyses;
		this.sites = sites;
		this.storage = storage;
		this.alertQuery = alertQuery;
		this.liveEvents = liveEvents;
	}

	/** The latest 50 analyses, newest first: for one site, or across all sites. */
	@GetMapping
	List<AnalysisView> list(@RequestParam(value = "siteId", required = false) UUID siteId) {
		var found = siteId == null ? analyses.findTop50ByOrderByCreatedAtDesc()
				: analyses.findTop50BySiteIdOrderByCreatedAtDesc(siteId);
		return found.stream().map(AnalysisView::of).toList();
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
	ResponseEntity<AnalysisDetail> get(@PathVariable UUID id) {
		return analyses.findById(id).map(a -> ResponseEntity.ok(new AnalysisDetail(a.id(), a.siteId(),
				a.status().name(), a.risk(), a.createdAt(), shortened(a.failureReason()), parse(a.id(), a.result()),
				alertQuery.forAnalysis(a.id()), alertQuery.officerReport(a.id()).orElse(null))))
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * Live updates (Server-Sent Events): an event on every change; the page re-reads the analysis each time. The
	 * browser's EventSource reconnects by itself when the stream ends.
	 */
	@GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter events(@PathVariable UUID id) {
		if (!analyses.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return liveEvents.subscribe(id);
	}

	/** Worker-supplied text: bounded, and shown by the dashboard as plain text only. */
	private static String shortened(String text) {
		return text == null || text.length() <= 500 ? text : text.substring(0, 500);
	}

	private Map<String, Object> parse(UUID analysisId, String resultJson) {
		if (resultJson == null) {
			return null;
		}
		try {
			return json.readValue(resultJson, new TypeReference<Map<String, Object>>() { });
		}
		catch (JacksonException e) {
			// One unreadable result must not hide the whole analysis, and must never read as "nothing found".
			log.warn("stored result of analysis {} is not valid JSON", analysisId);
			return Map.of("unparseable", true);
		}
	}

}
