package com.gajamitra.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The three queues between Spring and the AI service (plan Phase 3, Messaging) and a dead-letter queue. All durable;
 * a message no consumer can handle goes to analysis.dlq for a person to inspect, never silently away.
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

	static final String REQUESTS = "analysis.requests";
	static final String RESUME = "analysis.resume";
	static final String EVENTS = "analysis.events";
	static final String DEAD_LETTERS = "analysis.dlq";

	@Bean
	Queue requests() {
		return withDeadLetters(REQUESTS);
	}

	@Bean
	Queue resume() {
		return withDeadLetters(RESUME);
	}

	@Bean
	Queue events() {
		return withDeadLetters(EVENTS);
	}

	@Bean
	Queue deadLetters() {
		return QueueBuilder.durable(DEAD_LETTERS).build();
	}

	private static Queue withDeadLetters(String name) {
		return QueueBuilder.durable(name).deadLetterExchange("").deadLetterRoutingKey(DEAD_LETTERS).build();
	}

}
