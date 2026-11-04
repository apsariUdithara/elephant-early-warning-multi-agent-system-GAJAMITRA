package com.gajamitra.analysis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gajamitra.audit.AuditEntry.Actor;
import com.gajamitra.audit.AuditLog;
import com.gajamitra.messaging.MessagingRecovery;

/** Creates analyses. The upload endpoint (next slice) stores the video, then calls {@link #queue}. */
@Service
public class AnalysisService {

	private final AnalysisRepository analyses;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;

	AnalysisService(AnalysisRepository analyses, AuditLog audit, ApplicationEventPublisher events) {
		this.analyses = analyses;
		this.audit = audit;
		this.events = events;
	}

	/** Insert QUEUED and publish analysis.requests after commit (never inside the transaction). */
	@Transactional
	public UUID queue(UUID siteId, String videoPath, List<Double> boundary) {
		Analysis analysis = analyses.save(Analysis.queued(siteId, videoPath, boundary));
		audit.record(analysis.id(), Actor.SYSTEM, "analysis_queued", Map.of("site_id", siteId.toString()));
		events.publishEvent(MessagingRecovery.request(analysis));
		return analysis.id();
	}

}
