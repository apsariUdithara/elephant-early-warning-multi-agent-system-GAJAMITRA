package com.gajamitra.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.gajamitra.analysis.Analysis;
import com.gajamitra.analysis.AnalysisRepository;
import com.gajamitra.approval.ApprovalService;
import com.gajamitra.audit.AuditEntry.Actor;
import com.gajamitra.audit.AuditLog;
import com.gajamitra.messaging.Messages.Event;
import com.gajamitra.site.SiteRepository;

/**
 * Consumes analysis.events from the AI service (invariant 5: at least once, possibly out of order). Each event_id is
 * stored in processed_event in the same transaction as its effect, so a redelivered event changes nothing, and status
 * changes never move a finished analysis backwards. A message that can't be read is dead-lettered (analysis.dlq),
 * never dropped; a transient failure is retried by the listener (application.properties) before that.
 */
@Component
class EventsConsumer {

	static final Set<String> TYPES = Set.of("started", "approval_required", "completed", "failed");

	private static final Logger log = LoggerFactory.getLogger(EventsConsumer.class);

	private final AnalysisRepository analyses;
	private final SiteRepository sites;
	private final ApprovalService approvals;
	private final AuditLog audit;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;
	private final Publisher publisher;
	private final JsonMapper json = JsonMapper.builder().build();

	EventsConsumer(AnalysisRepository analyses, SiteRepository sites, ApprovalService approvals, AuditLog audit,
			JdbcTemplate jdbc, TransactionTemplate transaction, Publisher publisher) {
		this.publisher = publisher;
		this.analyses = analyses;
		this.sites = sites;
		this.approvals = approvals;
		this.audit = audit;
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	@RabbitListener(queues = MessagingConfiguration.EVENTS)
	void receive(Message message) {
		Event event;
		try {
			event = parse(message.getBody());
		}
		catch (AmqpRejectAndDontRequeueException e) {
			// Retrying can't fix an unreadable message: dead-letter it at once instead of blocking the queue.
			log.error("analysis.events message moved to {}: {}", MessagingConfiguration.DEAD_LETTERS, e.getMessage());
			publisher.send(MessagingConfiguration.DEAD_LETTERS, message.getBody());
			return;
		}
		try (var ignored = MDC.putCloseable("analysisId", event.analysis_id().toString())) {
			transaction.executeWithoutResult(status -> apply(event));
		}
	}

	Event parse(byte[] body) {
		Event event;
		try {
			event = json.readValue(new String(body, StandardCharsets.UTF_8), Event.class);
		}
		catch (JacksonException e) {
			throw new AmqpRejectAndDontRequeueException("analysis.events message is not valid JSON");
		}
		if (event.schema_version() == null || event.schema_version() != Messages.SCHEMA_VERSION
				|| event.event_id() == null || event.analysis_id() == null || !TYPES.contains(event.type())) {
			throw new AmqpRejectAndDontRequeueException("analysis.events message doesn't match schema_version 1");
		}
		if (("approval_required".equals(event.type()) && event.escalation() == null)
				|| ("failed".equals(event.type()) && (event.reason() == null || event.reason().isBlank()))) {
			throw new AmqpRejectAndDontRequeueException(event.type() + " event without its required field");
		}
		return event;
	}

	private void apply(Event event) {
		int fresh = jdbc.update("insert into processed_event (event_id) values (?) on conflict do nothing",
				event.event_id());
		if (fresh == 0) {
			return; // already handled: redelivery
		}
		Analysis analysis = analyses.lockById(event.analysis_id())
			.orElseThrow(() -> new AmqpRejectAndDontRequeueException("event for an unknown analysis"));
		boolean changed = switch (event.type()) {
			case "started" -> analysis.markRunning();
			case "approval_required" -> {
				// Late (after completed or failed): no approval to open, nothing to time out later.
				boolean waiting = analysis.awaitApproval();
				if (waiting) {
					var site = sites.findById(analysis.siteId()).orElseThrow();
					approvals.open(analysis.id(), json.writeValueAsString(event.escalation()), site.approvalTimeout());
				}
				yield waiting;
			}
			case "completed" -> analysis.complete(json.writeValueAsString(event.result() == null ? Map.of()
					: event.result()));
			case "failed" -> {
				// A failed analysis may have left a herd unwarned: make it loud (invariant 10).
				log.error("analysis {} failed in the AI service: {}", analysis.id(), event.reason());
				yield analysis.fail(event.reason());
			}
			default -> false;
		};
		audit.record(analysis.id(), Actor.AGENT, "event_" + event.type(),
				Map.of("event_id", event.event_id().toString(), "status_changed", changed));
		if (changed) {
			publisher.changed(analysis.id(), event.type());
		}
	}

}
