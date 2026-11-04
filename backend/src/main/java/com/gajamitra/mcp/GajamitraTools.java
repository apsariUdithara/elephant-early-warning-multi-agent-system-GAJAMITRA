package com.gajamitra.mcp;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Validator;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.gajamitra.alert.Alert;
import com.gajamitra.alert.AlertDispatchService;
import com.gajamitra.alert.ToolResult;
import com.gajamitra.alert.VillageAlert;
import com.gajamitra.analysis.AnalysisRepository;
import com.gajamitra.site.Site;
import com.gajamitra.site.SiteRepository;

/**
 * The MCP tools the AI service calls (plan Phase 3, "MCP server"; Python side: ai-service/gajamitra/ports.py
 * ToolClient). Every argument is untrusted: ids are parsed, alerts are validated, and anything malformed is answered
 * with a rejection, never an exception the agent could misread. The decisions are made in {@link AlertDispatchService}.
 */
@Component
public class GajamitraTools {

	/** Same limit as ai-service schemas.MAX_REPORT_CHARS. */
	static final int MAX_REPORT_CHARS = 2000;

	/** A tracker summary is a few hundred characters; anything far larger is refused. */
	static final int MAX_SUMMARY_CHARS = 16_000;

	/** "{uuid}:escalation:999" is 51 characters. The key's format is checked by AlertDispatchService. */
	static final int MAX_KEY_CHARS = 100;

	/** Most days of history an agent may ask for. */
	static final int MAX_HISTORY_DAYS = 90;

	private final AlertDispatchService dispatch;
	private final SiteRepository sites;
	private final AnalysisRepository analyses;
	private final Validator validator;
	private final Clock clock;
	private final JsonMapper json = JsonMapper.builder().build();

	GajamitraTools(AlertDispatchService dispatch, SiteRepository sites, AnalysisRepository analyses,
			Validator validator, Clock clock) {
		this.dispatch = dispatch;
		this.sites = sites;
		this.analyses = analyses;
		this.validator = validator;
		this.clock = clock;
	}

	@McpTool(name = "record_assessment", description = "Store the deterministic tracker's risk for an analysis "
			+ "before any model runs. Answers {status: recorded} or {status: rejected, reason}.")
	public ToolResult recordAssessment(@McpToolParam(description = "analysis id (UUID)") String analysis_id,
			@McpToolParam(description = "low, medium, high or critical") String risk,
			@McpToolParam(description = "tracker summary") Map<String, Object> summary) {
		Optional<UUID> id = uuid(analysis_id);
		if (id.isEmpty()) {
			return ToolResult.rejected("analysis_id is not a UUID");
		}
		String summaryJson = json.writeValueAsString(summary == null ? Map.of() : summary);
		if (summaryJson.length() > MAX_SUMMARY_CHARS) {
			return ToolResult.rejected("summary is larger than " + MAX_SUMMARY_CHARS + " characters");
		}
		return dispatch.recordAssessment(id.get(), risk, summaryJson);
	}

	@McpTool(name = "send_village_alert", description = "Send a village alert in English, Sinhala and Tamil. "
			+ "Spring re-checks it against the stored tracker risk and the approval. Answers {status: accepted} "
			+ "or {status: rejected, reason}.")
	public ToolResult sendVillageAlert(@McpToolParam(description = "analysis id (UUID)") String analysis_id,
			@McpToolParam(description = "{analysis_id}:{now|escalation}:{index}") String idempotency_key,
			@McpToolParam(description = "NOW or ESCALATION") String stage,
			@McpToolParam(description = "severity (advisory, warning, danger) and text en, si, ta") VillageAlert alert) {
		Optional<UUID> id = uuid(analysis_id);
		if (id.isEmpty()) {
			return ToolResult.rejected("analysis_id is not a UUID");
		}
		Alert.Stage parsed = "NOW".equals(stage) ? Alert.Stage.NOW
				: "ESCALATION".equals(stage) ? Alert.Stage.ESCALATION : null;
		if (parsed == null) {
			return ToolResult.rejected("stage must be NOW or ESCALATION");
		}
		if (idempotency_key == null || idempotency_key.length() > MAX_KEY_CHARS) {
			return ToolResult.rejected("idempotency_key is missing or too long");
		}
		if (alert == null || !validator.validate(alert).isEmpty()) {
			return ToolResult.rejected("alert needs a severity and en, si and ta text of at most 320 characters");
		}
		return dispatch.sendVillageAlert(id.get(), idempotency_key, parsed, alert);
	}

	@McpTool(name = "notify_wildlife_officer", description = "Store the report for the wildlife officer. "
			+ "Answers {status: accepted} or {status: rejected, reason}.")
	public ToolResult notifyWildlifeOfficer(@McpToolParam(description = "analysis id (UUID)") String analysis_id,
			@McpToolParam(description = "{analysis_id}:now:report") String idempotency_key,
			@McpToolParam(description = "report text") String report) {
		Optional<UUID> id = uuid(analysis_id);
		if (id.isEmpty()) {
			return ToolResult.rejected("analysis_id is not a UUID");
		}
		if (idempotency_key == null || idempotency_key.length() > MAX_KEY_CHARS) {
			return ToolResult.rejected("idempotency_key is missing or too long");
		}
		if (report == null || report.isBlank() || report.length() > MAX_REPORT_CHARS) {
			return ToolResult.rejected("report must be 1 to " + MAX_REPORT_CHARS + " characters");
		}
		return dispatch.notifyWildlifeOfficer(id.get(), idempotency_key, report);
	}

	/** Procedural memory: where the site is, who to tell, whether escalations need an officer. */
	public record SiteProcedures(String name, String villages, boolean requires_approval) {
	}

	@McpTool(name = "get_site_procedures", description = "The site's name, villages and approval rule.")
	public SiteProcedures getSiteProcedures(@McpToolParam(description = "site id (UUID)") String site_id) {
		// Unknown site: no name, approval required (the safe default, as in ai-service's stub).
		return uuid(site_id).flatMap(sites::findById)
			.map((Site s) -> new SiteProcedures(s.name(), s.villages(), s.requiresApproval()))
			.orElse(new SiteProcedures(null, "", true));
	}

	/** One past assessment at the site. No video paths, phones or alert text. */
	public record Incident(String analysis_id, String at, String risk) {
	}

	@McpTool(name = "get_site_history", description = "Assessed analyses at the site in the last N days (at most "
			+ MAX_HISTORY_DAYS + "), newest first, at most 50.")
	public List<Incident> getSiteHistory(@McpToolParam(description = "site id (UUID)") String site_id,
			@McpToolParam(description = "days to look back") int days) {
		Optional<UUID> id = uuid(site_id);
		if (id.isEmpty() || days < 1) {
			return List.of();
		}
		var after = clock.instant().minus(Duration.ofDays(Math.min(days, MAX_HISTORY_DAYS)));
		return analyses.findTop50BySiteIdAndRiskNotNullAndCreatedAtAfterOrderByCreatedAtDesc(id.get(), after)
			.stream()
			.map(a -> new Incident(a.id().toString(), String.valueOf(a.createdAt()), a.risk()))
			.toList();
	}

	private static Optional<UUID> uuid(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		}
		catch (IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
	}

}
