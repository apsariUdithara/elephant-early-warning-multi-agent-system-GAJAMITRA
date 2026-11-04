package com.gajamitra.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * The Java re-check against the shared policy/policy_table.json. The same table is asserted by
 * ai-service/tests/test_policy.py, so the two guardrails can't drift apart (plan Verification, "Shared guard table").
 */
class AlertPolicyTests {

	private final AlertPolicy policy = new AlertPolicy();

	@ParameterizedTest(name = "{0} risk, {1} {2} -> {3}")
	@CsvSource({
			// risk,    stage,      severity, allowed
			"low,      NOW,        advisory, false",
			"low,      NOW,        warning,  false",
			"medium,   NOW,        warning,  true",
			"medium,   NOW,        advisory, true",
			"medium,   NOW,        danger,   false",
			"medium,   ESCALATION, warning,  false",
			"high,     NOW,        warning,  true",
			"high,     NOW,        danger,   false", // would skip the officer's approval (invariant 2)
			"high,     ESCALATION, danger,   true",
			"critical, NOW,        danger,   true",
			"critical, ESCALATION, danger,   false", // critical is never an escalation: it goes out at once
	})
	void severityIsCheckedAgainstTheStoredRiskAndStage(String risk, String stage, String severity, boolean allowed) {
		assertThat(policy.check(risk, stage, severity).allowed()).isEqualTo(allowed);
	}

	@ParameterizedTest
	@NullSource
	void noStoredRiskFailsClosed(String risk) {
		// Invariant 3: Spring rejects an alert when it has no stored tracker risk for the analysis.
		AlertPolicy.Check check = policy.check(risk, "NOW", "warning");
		assertThat(check.allowed()).isFalse();
		assertThat(check.reason()).contains("no stored tracker risk");
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown", "HIGH", "" })
	void unknownRiskIsRejected(String risk) {
		assertThat(policy.check(risk, "NOW", "warning").allowed()).isFalse();
	}

	@Test
	void unknownStageOrSeverityIsRejected() {
		assertThat(policy.check("high", "LATER", "warning").allowed()).isFalse();
		assertThat(policy.check("high", "NOW", "emergency").allowed()).isFalse();
	}

	@Test
	void sharedTableContract() {
		// Same rows as test_policy_table_contract in ai-service: change both if the table changes.
		var table = loadedTable();
		assertThat(table.severityOrder()).containsExactly("advisory", "warning", "danger");
		assertThat(table.risk()).containsExactlyInAnyOrderEntriesOf(Map.of(
				"low", new AlertPolicy.Rule(null, null, null, false, false),
				"medium", new AlertPolicy.Rule("warning", "warning", null, false, false),
				"high", new AlertPolicy.Rule("danger", "warning", "danger", true, true),
				"critical", new AlertPolicy.Rule("danger", "danger", null, true, false)));
	}

	private static AlertPolicy.Table loadedTable() {
		try (var in = AlertPolicy.class.getClassLoader().getResourceAsStream(AlertPolicy.TABLE)) {
			return tools.jackson.databind.json.JsonMapper.builder().build().readValue(in, AlertPolicy.Table.class);
		}
		catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

}
