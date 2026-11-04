package com.gajamitra.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Appends to the audit log inside the caller's transaction, so an action and its audit line commit together. */
@Component
public class AuditLog {

	private final AuditEntryRepository entries;

	private final JsonMapper json = JsonMapper.builder().build();

	AuditLog(AuditEntryRepository entries) {
		this.entries = entries;
	}

	public void record(UUID analysisId, AuditEntry.Actor actor, String action, Map<String, ?> payload) {
		entries.save(new AuditEntry(analysisId, actor, action, json.writeValueAsString(payload)));
	}

}
