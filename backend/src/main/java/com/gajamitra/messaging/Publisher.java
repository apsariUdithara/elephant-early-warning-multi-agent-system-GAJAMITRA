package com.gajamitra.messaging;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import com.gajamitra.analysis.AnalysisChanged;
import com.gajamitra.messaging.Messages.AnalysisRequest;
import com.gajamitra.messaging.Messages.Resume;

/**
 * Publishes to the AI service only after the database transaction commits, never inside it (messaging rules). If the
 * broker is down at that moment the message is lost, so {@link MessagingRecovery} republishes from the database:
 * stale QUEUED analyses and decided approvals whose analysis is still waiting.
 */
@Component
public class Publisher {

	private final RabbitTemplate rabbit;

	private final org.springframework.context.ApplicationEventPublisher events;

	private final JsonMapper json = JsonMapper.builder().build();

	Publisher(RabbitTemplate rabbit, org.springframework.context.ApplicationEventPublisher events) {
		this.rabbit = rabbit;
		this.events = events;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onRequest(AnalysisRequest request) {
		send(MessagingConfiguration.REQUESTS, request);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onResume(Resume resume) {
		send(MessagingConfiguration.RESUME, resume);
	}

	/** Tell open dashboards (after commit) that an analysis changed. */
	void changed(java.util.UUID analysisId, String kind) {
		events.publishEvent(new AnalysisChanged(analysisId, kind));
	}

	void send(String queue, Object body) {
		byte[] bytes = body instanceof byte[] raw ? raw : json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
		var properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		rabbit.send("", queue, new Message(bytes, properties));
	}

}
