package com.gajamitra.alert;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gajamitra.analysis.Analysis;
import com.gajamitra.analysis.AnalysisChanged;
import com.gajamitra.analysis.AnalysisRepository;
import com.gajamitra.approval.ApprovalRequestRepository;
import com.gajamitra.audit.AuditEntry.Actor;
import com.gajamitra.audit.AuditLog;
import com.gajamitra.site.Site;
import com.gajamitra.site.SiteRepository;
import com.gajamitra.site.SubscriberRepository;

/**
 * The server side of the AI service's side-effect tools (plan Phase 3, MCP server). Python proposes; this class
 * re-checks and decides, writes the audit log, and is the only place that creates alerts.
 * <p>
 * Each call locks the analysis row, so calls for one analysis run one at a time: a repeated idempotency key finds
 * the stored result instead of racing to insert (invariant 4). SMS go out after commit ({@link AlertSender}).
 */
@Service
public class AlertDispatchService {

	private final AnalysisRepository analyses;
	private final SiteRepository sites;
	private final SubscriberRepository subscribers;
	private final AlertRepository alerts;
	private final AlertDeliveryRepository deliveries;
	private final OfficerReportRepository reports;
	private final ApprovalRequestRepository approvals;
	private final AlertPolicy policy;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;

	AlertDispatchService(AnalysisRepository analyses, SiteRepository sites, SubscriberRepository subscribers,
			AlertRepository alerts, AlertDeliveryRepository deliveries, OfficerReportRepository reports,
			ApprovalRequestRepository approvals, AlertPolicy policy, AuditLog audit, ApplicationEventPublisher events) {
		this.analyses = analyses;
		this.sites = sites;
		this.subscribers = subscribers;
		this.alerts = alerts;
		this.deliveries = deliveries;
		this.reports = reports;
		this.approvals = approvals;
		this.policy = policy;
		this.audit = audit;
		this.events = events;
	}

	/** MCP record_assessment: store the tracker's risk before any model runs, so the re-check can fail closed. */
	@Transactional
	public ToolResult recordAssessment(UUID analysisId, String risk, String summaryJson) {
		Optional<Analysis> found = analyses.lockById(analysisId);
		if (found.isEmpty()) {
			return ToolResult.rejected("unknown analysis");
		}
		if (!Analysis.RISKS.contains(risk)) {
			return reject(analysisId, "record_assessment", "unknown risk '" + risk + "'", Map.of());
		}
		if (!found.get().recordRisk(risk, summaryJson)) {
			return reject(analysisId, "record_assessment",
					"risk is already " + found.get().risk() + " and can't be changed to " + risk, Map.of());
		}
		audit.record(analysisId, Actor.AGENT, "risk_recorded", Map.of("risk", risk));
		events.publishEvent(new AnalysisChanged(analysisId, "risk"));
		return ToolResult.recorded();
	}

	/** MCP send_village_alert: the second guardrail (invariants 2, 3, 4, 8, 9). */
	@Transactional
	public ToolResult sendVillageAlert(UUID analysisId, String key, Alert.Stage stage, VillageAlert proposed) {
		Optional<Analysis> found = analyses.lockById(analysisId);
		if (found.isEmpty()) {
			return ToolResult.rejected("unknown analysis");
		}
		String keyProblem = keyProblem(analysisId, key, stage == Alert.Stage.NOW ? "now" : "escalation", false);
		if (keyProblem != null) {
			return reject(analysisId, "village_alert_rejected", keyProblem, Map.of("key", String.valueOf(key)));
		}
		if (alerts.findByIdempotencyKey(key).isPresent()) {
			return ToolResult.accepted(); // already done: send nothing again
		}
		Analysis analysis = found.get();
		AlertPolicy.Check check = policy.check(analysis.risk(), stage.name(), proposed.severity());
		if (!check.allowed()) {
			return reject(analysisId, "village_alert_rejected", check.reason(),
					Map.of("key", key, "severity", String.valueOf(proposed.severity())));
		}
		Site site = sites.findById(analysis.siteId()).orElseThrow();
		if (stage == Alert.Stage.ESCALATION && site.requiresApproval() && !approvals.escalationReleased(analysisId)) {
			return reject(analysisId, "village_alert_rejected", "escalation not approved and not timed out",
					Map.of("key", key));
		}
		Alert alert = alerts.save(new Alert(analysisId, key, stage, proposed, site.shadow()));
		subscribers.findBySiteId(site.id())
			.forEach(s -> deliveries.save(new AlertDelivery(alert.id(), s.id(), site.shadow())));
		audit.record(analysisId, Actor.AGENT, "village_alert_accepted",
				Map.of("key", key, "stage", stage.name(), "severity", alert.severity(), "shadow", site.shadow()));
		events.publishEvent(new AlertSender.AlertStored(analysisId, alert.id()));
		events.publishEvent(new AnalysisChanged(analysisId, "alert"));
		return ToolResult.accepted();
	}

	/** MCP notify_wildlife_officer: stored once per key, like alerts. Needs a stored risk too. */
	@Transactional
	public ToolResult notifyWildlifeOfficer(UUID analysisId, String key, String report) {
		Optional<Analysis> found = analyses.lockById(analysisId);
		if (found.isEmpty()) {
			return ToolResult.rejected("unknown analysis");
		}
		String keyProblem = keyProblem(analysisId, key, "now", true);
		if (keyProblem != null) {
			return reject(analysisId, "officer_report_rejected", keyProblem, Map.of("key", String.valueOf(key)));
		}
		if (reports.findByIdempotencyKey(key).isPresent()) {
			return ToolResult.accepted();
		}
		if (found.get().risk() == null) {
			return reject(analysisId, "officer_report_rejected", "no stored tracker risk for this analysis", Map.of());
		}
		reports.save(new OfficerReport(analysisId, key, report));
		audit.record(analysisId, Actor.AGENT, "officer_report_stored", Map.of("key", key));
		events.publishEvent(new AnalysisChanged(analysisId, "report"));
		return ToolResult.accepted();
	}

	/** Keys are {analysis_id}:{now|escalation}:{index}, and {analysis_id}:now:report for the officer report. */
	private static String keyProblem(UUID analysisId, String key, String stage, boolean report) {
		String prefix = analysisId + ":" + stage + ":";
		if (key == null || !key.startsWith(prefix)) {
			return "idempotency key doesn't belong to this analysis and stage";
		}
		String rest = key.substring(prefix.length());
		boolean valid = report ? rest.equals("report") : rest.matches("\\d{1,3}");
		return valid ? null : "malformed idempotency key";
	}

	private ToolResult reject(UUID analysisId, String action, String reason, Map<String, Object> details) {
		var payload = new HashMap<String, Object>(details);
		payload.put("reason", reason);
		audit.record(analysisId, Actor.SYSTEM, action, payload);
		return ToolResult.rejected(reason);
	}

}
