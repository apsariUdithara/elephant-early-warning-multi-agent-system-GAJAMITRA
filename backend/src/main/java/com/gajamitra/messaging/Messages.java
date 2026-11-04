package com.gajamitra.messaging;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The message contract with the AI service, schema_version 1 (plan Phase 4; Python side: ai-service worker.py).
 * Fields may be added; renaming or removing one needs a schema_version bump on both sides.
 */
public final class Messages {

	public static final int SCHEMA_VERSION = 1;

	private Messages() {
	}

	/** analysis.requests: start (or, if a checkpoint exists, continue) the graph for one analysis. */
	public record AnalysisRequest(int schema_version, String analysis_id, String site_id, String video_path,
			List<Double> boundary) {
	}

	/** analysis.resume: the officer's decision, or "timeout" when the approval expired (invariant 9). */
	public record Resume(int schema_version, String analysis_id, String decision, String officer, String note) {
	}

	/**
	 * analysis.events, from the AI service. type is started, approval_required, completed or failed. event_id makes
	 * redelivery harmless: the consumer stores each id once (invariant 5).
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Event(Integer schema_version, UUID event_id, UUID analysis_id, String type, Object escalation,
			Map<String, Object> result, String reason) {
	}

}
