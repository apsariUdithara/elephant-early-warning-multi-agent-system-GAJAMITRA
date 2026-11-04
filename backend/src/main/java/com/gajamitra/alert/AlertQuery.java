package com.gajamitra.alert;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.gajamitra.site.Subscriber.Language;

/** Read side for the dashboard: what was sent for an analysis. No phone numbers, only delivery counts. */
@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class AlertQuery {

	/** One accepted alert in all three languages, with how many villagers are in each delivery state. */
	public record AlertView(String stage, String severity, boolean shadow, String en, String si, String ta,
			Instant createdAt, Map<String, Long> deliveries) {
	}

	private final AlertRepository alerts;
	private final AlertDeliveryRepository deliveries;
	private final OfficerReportRepository reports;

	AlertQuery(AlertRepository alerts, AlertDeliveryRepository deliveries, OfficerReportRepository reports) {
		this.alerts = alerts;
		this.deliveries = deliveries;
		this.reports = reports;
	}

	public List<AlertView> forAnalysis(UUID analysisId) {
		List<Alert> found = alerts.findByAnalysisIdOrderByCreatedAt(analysisId);
		if (found.isEmpty()) {
			return List.of();
		}
		// One query for every alert's delivery counts (not one per alert).
		Map<UUID, Map<String, Long>> counts = new HashMap<>();
		for (Object[] row : deliveries.countByAlertAndStatus(found.stream().map(Alert::id).toList())) {
			counts.computeIfAbsent((UUID) row[0], k -> new HashMap<>()).put(((AlertDelivery.Status) row[1]).name(),
					(Long) row[2]);
		}
		return found.stream()
			.map(a -> new AlertView(a.stage().name(), a.severity(), a.shadow(), a.textFor(Language.EN),
					a.textFor(Language.SI), a.textFor(Language.TA), a.createdAt(), counts.getOrDefault(a.id(), Map.of())))
			.toList();
	}

	public Optional<String> officerReport(UUID analysisId) {
		return reports.findByAnalysisId(analysisId).map(OfficerReport::report);
	}

}
