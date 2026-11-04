package com.gajamitra.alert;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The second guardrail: Spring's re-check of every village alert the AI service asks to send (plan Phase 3, MCP
 * {@code send_village_alert}). Reads {@code policy/policy_table.json}, the same file as ai-service's {@code policy.py},
 * so the two guardrails can't drift apart. A missing or inconsistent table stops the application from starting.
 */
@Component
public final class AlertPolicy {

	static final String TABLE = "policy/policy_table.json";
	private static final Set<String> RISKS = Set.of("low", "medium", "high", "critical");
	private static final Set<String> STAGES = Set.of("NOW", "ESCALATION");

	private final Table table;

	public AlertPolicy() {
		this(AlertPolicy.class.getClassLoader().getResourceAsStream(TABLE));
	}

	AlertPolicy(InputStream table) {
		this.table = load(table);
	}

	/** Whether an alert at {@code severity} may be sent at {@code stage} for an analysis whose stored risk is {@code risk}. */
	public Check check(String risk, String stage, String severity) {
		if (risk == null) {
			return Check.reject("no stored tracker risk for this analysis"); // invariant 3: fail closed
		}
		Rule rule = table.risk().get(risk);
		if (rule == null) {
			return Check.reject("unknown tracker risk '" + risk + "'");
		}
		if (stage == null || !STAGES.contains(stage)) {
			return Check.reject("unknown stage '" + stage + "'");
		}
		int level = rank(severity);
		if (level < 0) {
			return Check.reject("unknown severity '" + severity + "'");
		}
		if (rule.maxSeverity() == null) {
			return Check.reject("no village alert is allowed at " + risk + " risk");
		}
		if (level > rank(rule.maxSeverity())) {
			return Check.reject(severity + " is above the maximum for " + risk + " risk");
		}
		String limit = stage.equals("NOW") ? rule.sendNowSeverity() : rule.escalationSeverity();
		if (limit == null || level > rank(limit)) {
			// For example a "danger" alert sent as NOW at high risk would skip the officer's approval (invariant 2).
			return Check.reject(severity + " is not allowed at stage " + stage + " for " + risk + " risk");
		}
		return Check.ALLOWED;
	}

	private int rank(String severity) {
		return table.severityOrder().indexOf(severity);
	}

	private static Table load(InputStream source) {
		try (InputStream in = source) {
			if (in == null) {
				throw new IllegalStateException(TABLE + " is missing from the classpath");
			}
			Table table = JsonMapper.builder().build().readValue(in, Table.class);
			validate(table);
			return table;
		}
		catch (IOException e) {
			throw new UncheckedIOException("cannot read " + TABLE, e);
		}
	}

	private static void validate(Table table) {
		if (table.severityOrder() == null || table.risk() == null || !table.risk().keySet().equals(RISKS)) {
			throw new IllegalStateException(TABLE + " must define severity_order and every risk level " + RISKS);
		}
		List<String> order = table.severityOrder();
		if (order.contains(null) || Set.copyOf(order).size() != order.size()) {
			throw new IllegalStateException(TABLE + ": severity_order must list each level once");
		}
		table.risk().forEach((risk, rule) -> {
			if (rule == null || rule.officerReport() == null || rule.holdEscalation() == null) {
				throw new IllegalStateException(TABLE + ": " + risk + " must set officer_report and hold_escalation");
			}
			for (String level : new String[] { rule.maxSeverity(), rule.sendNowSeverity(), rule.escalationSeverity() }) {
				// A typo such as "warnng" would otherwise reject every alert at that risk, losing the warning.
				if (level != null && !order.contains(level)) {
					throw new IllegalStateException(TABLE + ": " + risk + " uses unknown severity '" + level + "'");
				}
			}
			for (String level : new String[] { rule.sendNowSeverity(), rule.escalationSeverity() }) {
				if (level != null && (rule.maxSeverity() == null
						|| table.severityOrder().indexOf(level) > table.severityOrder().indexOf(rule.maxSeverity()))) {
					throw new IllegalStateException(TABLE + ": " + risk + " sends " + level + " above its max_severity");
				}
			}
		});
	}

	/** Result of {@link #check}: allowed, or rejected with a reason for the audit log. */
	public record Check(boolean allowed, String reason) {

		static final Check ALLOWED = new Check(true, null);

		static Check reject(String reason) {
			return new Check(false, reason);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Table(@JsonProperty("severity_order") List<String> severityOrder, @JsonProperty("risk") Map<String, Rule> risk) {
	}

	record Rule(@JsonProperty("max_severity") String maxSeverity,
			@JsonProperty("send_now_severity") String sendNowSeverity,
			@JsonProperty("escalation_severity") String escalationSeverity,
			@JsonProperty("officer_report") Boolean officerReport,
			@JsonProperty("hold_escalation") Boolean holdEscalation) {
	}

}
