package com.gajamitra.analysis;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live updates to dashboards over Server-Sent Events (plan Phase 3, REST API). One registry for every open stream:
 * an emitter is removed on completion, timeout, error or a failed send, so closed browsers don't pile up. Events are
 * sent after the database commit, never inside the transaction. A heartbeat every 20 s keeps idle streams alive
 * through proxies and finds dead ones.
 */
@Component
public class AnalysisEvents {

	/** A stream ends after this long; the browser reconnects (EventSource does it on its own). */
	static final Duration TIMEOUT = Duration.ofMinutes(30);

	/** Open streams in all, and per analysis: past these a new stream is refused with 429. */
	static final int MAX_STREAMS = 200;

	static final int MAX_STREAMS_PER_ANALYSIS = 20;

	private static final Logger log = LoggerFactory.getLogger(AnalysisEvents.class);

	private final ConcurrentHashMap<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

	SseEmitter subscribe(UUID analysisId) {
		if (subscribers() >= MAX_STREAMS || subscribers(analysisId) >= MAX_STREAMS_PER_ANALYSIS) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many open live streams");
		}
		var emitter = new SseEmitter(TIMEOUT.toMillis());
		emitters.computeIfAbsent(analysisId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
		Runnable remove = () -> remove(analysisId, emitter);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(e -> remove.run());
		send(analysisId, emitter, SseEmitter.event().name("analysis").data("{\"kind\":\"subscribed\"}"));
		return emitter;
	}

	/** Open streams for an analysis. */
	int subscribers(UUID analysisId) {
		return emitters.getOrDefault(analysisId, Set.of()).size();
	}

	/** Open streams in all. */
	int subscribers() {
		return emitters.values().stream().mapToInt(Set::size).sum();
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onChanged(AnalysisChanged changed) {
		var event = SseEmitter.event().name("analysis").data("{\"kind\":\"" + changed.kind() + "\"}");
		for (SseEmitter emitter : Set.copyOf(emitters.getOrDefault(changed.analysisId(), Set.of()))) {
			send(changed.analysisId(), emitter, event);
		}
	}

	/** A comment line every 20 s: keeps proxies from cutting an idle stream and drops the ones already closed. */
	@Scheduled(initialDelay = 20_000, fixedDelay = 20_000)
	public void heartbeat() {
		var ping = SseEmitter.event().comment("ping");
		emitters.forEach((analysisId, set) -> Set.copyOf(set).forEach(emitter -> send(analysisId, emitter, ping)));
	}

	private void send(UUID analysisId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
		try (var ignored = MDC.putCloseable("analysisId", analysisId.toString())) {
			// Several threads (queue consumer, schedulers, requests) may send to one stream: one at a time.
			synchronized (emitter) {
				emitter.send(event);
			}
		}
		catch (IOException | RuntimeException e) {
			log.debug("dropping a closed stream", e); // the browser reconnects and re-reads the analysis
			remove(analysisId, emitter);
		}
	}

	private void remove(UUID analysisId, SseEmitter emitter) {
		emitters.computeIfPresent(analysisId, (id, set) -> {
			set.remove(emitter);
			return set.isEmpty() ? null : set;
		});
	}

}
