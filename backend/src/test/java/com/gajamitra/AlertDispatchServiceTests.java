package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.gajamitra.alert.Alert.Stage;
import com.gajamitra.alert.AlertDispatchService;
import com.gajamitra.alert.ToolResult;
import com.gajamitra.alert.VillageAlert;
import com.gajamitra.sms.SmsGateway;

/**
 * The second guardrail and at-most-once delivery against a real PostgreSQL (plan Phase 3 exit criteria: guard rejecting
 * an over-severe alert, no stored risk, same key twice, SHADOW vs LIVE).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AlertDispatchServiceTests {

	static final VillageAlert WARNING = new VillageAlert("warning", "Elephants near the fence.", "සිංහල", "தமிழ்");
	static final VillageAlert DANGER = new VillageAlert("danger", "Elephants at the fence.", "සිංහල", "தமிழ்");

	@Autowired
	AlertDispatchService service;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	SmsGateway sms;

	UUID analysis;

	String sinhalaPhone;

	String tamilPhone;

	@BeforeEach
	void seed() {
		reset(sms);
		when(sms.send(anyString(), anyString())).thenReturn("SM1");
		analysis = newAnalysis("LIVE", true);
	}

	UUID newAnalysis(String mode, boolean requiresApproval) {
		UUID site = jdbc.queryForObject("insert into site (name, mode, requires_approval) values ('Galgamuwa', ?, ?) returning id",
				UUID.class, mode, requiresApproval);
		sinhalaPhone = "+9477" + (1_000_000 + System.nanoTime() % 8_000_000);
		tamilPhone = "+9476" + (1_000_000 + System.nanoTime() % 8_000_000);
		jdbc.update("insert into subscriber (site_id, phone, language) values (?, ?, 'SI'), (?, ?, 'TA')", site,
				sinhalaPhone, site, tamilPhone);
		return jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, '/data/v.mp4', '{0,0.5,1,0.5,0.5,0.9}')
				returning id""", UUID.class, site);
	}

	String key(String stage, int i) {
		return analysis + ":" + stage + ":" + i;
	}

	int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class, analysis);
	}

	List<String> auditActions() {
		return jdbc.queryForList("select action from audit_log where analysis_id = ? order by at", String.class, analysis);
	}

	@Test
	void noStoredRiskRejectsTheAlertAndWritesNothing() { // invariant 3
		ToolResult result = service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, WARNING);
		assertThat(result.status()).isEqualTo("rejected");
		assertThat(result.reason()).contains("no stored tracker risk");
		assertThat(count("select count(*) from alert where analysis_id = ?")).isZero();
		assertThat(auditActions()).containsExactly("village_alert_rejected");
		verify(sms, never()).send(anyString(), anyString());
	}

	@Test
	void anAlertAboveWhatTheRiskAllowsIsRejected() { // invariant 8
		service.recordAssessment(analysis, "medium", "{}");
		assertThat(service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, DANGER).status()).isEqualTo("rejected");
		assertThat(count("select count(*) from alert where analysis_id = ?")).isZero();
	}

	@Test
	void anAcceptedLiveAlertTextsEveryVillagerInTheirLanguageOnce() {
		service.recordAssessment(analysis, "high", "{}");
		assertThat(service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, WARNING).status()).isEqualTo("accepted");
		verify(sms).send(sinhalaPhone, "සිංහල");
		verify(sms).send(tamilPhone, "தமிழ்");
		assertThat(jdbc.queryForList("""
				select d.status from alert_delivery d join alert a on a.id = d.alert_id where a.analysis_id = ?""",
				String.class, analysis)).containsOnly("SENT");
	}

	@Test
	void theSameKeyTwiceStoresOneAlertAndSendsOnce() { // invariant 4
		service.recordAssessment(analysis, "high", "{}");
		service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, WARNING);
		assertThat(service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, WARNING).status()).isEqualTo("accepted");
		assertThat(count("select count(*) from alert where analysis_id = ?")).isOne();
		verify(sms, times(2)).send(anyString(), anyString()); // two villagers, once each
	}

	@Test
	void aShadowSiteStoresTheAlertButSendsNoSms() {
		analysis = newAnalysis("SHADOW", true);
		service.recordAssessment(analysis, "critical", "{}");
		assertThat(service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, DANGER).status()).isEqualTo("accepted");
		verify(sms, never()).send(anyString(), anyString());
		assertThat(jdbc.queryForList("""
				select d.status from alert_delivery d join alert a on a.id = d.alert_id where a.analysis_id = ?""",
				String.class, analysis)).containsOnly("SKIPPED_SHADOW");
	}

	@Test
	void anEscalationWaitsForTheOfficerUnlessTheApprovalTimedOut() { // invariants 2 and 9
		service.recordAssessment(analysis, "high", "{}");
		jdbc.update("insert into approval_request (analysis_id, proposed_alert, expires_at) values (?, '{}', now())",
				analysis);
		assertThat(service.sendVillageAlert(analysis, key("escalation", 0), Stage.ESCALATION, DANGER).reason())
			.contains("not approved");
		jdbc.update("update approval_request set status = 'TIMED_OUT', decided_at = now() where analysis_id = ?", analysis);
		assertThat(service.sendVillageAlert(analysis, key("escalation", 0), Stage.ESCALATION, DANGER).status())
			.isEqualTo("accepted");
	}

	@Test
	void anEscalationNeedsNoApprovalWhereTheSiteDoesntRequireIt() {
		analysis = newAnalysis("LIVE", false);
		service.recordAssessment(analysis, "high", "{}");
		assertThat(service.sendVillageAlert(analysis, key("escalation", 0), Stage.ESCALATION, DANGER).status())
			.isEqualTo("accepted");
	}

	@Test
	void aKeyFromAnotherAnalysisOrStageIsRejected() { // invariant 4
		service.recordAssessment(analysis, "high", "{}");
		assertThat(service.sendVillageAlert(analysis, UUID.randomUUID() + ":now:0", Stage.NOW, WARNING).status())
			.isEqualTo("rejected");
		assertThat(service.sendVillageAlert(analysis, key("escalation", 0), Stage.NOW, WARNING).status())
			.isEqualTo("rejected");
		assertThat(service.sendVillageAlert(analysis, analysis + ":now:x", Stage.NOW, WARNING).status())
			.isEqualTo("rejected");
	}

	@Test
	void aFailedSmsIsRecordedAsFailedNotSent() {
		when(sms.send(eq(sinhalaPhone), anyString())).thenThrow(new IllegalStateException("provider down"));
		service.recordAssessment(analysis, "high", "{}");
		service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, WARNING);
		assertThat(jdbc.queryForList("""
				select d.status from alert_delivery d join alert a on a.id = d.alert_id where a.analysis_id = ?
				order by d.status""", String.class, analysis)).containsExactly("FAILED", "SENT");
	}

	@Test
	void theRiskIsRecordedOnceAndCantBeChanged() {
		assertThat(service.recordAssessment(analysis, "high", "{\"herd_size\": 3}").status()).isEqualTo("recorded");
		assertThat(service.recordAssessment(analysis, "high", "{}").status()).isEqualTo("recorded");
		assertThat(service.recordAssessment(analysis, "low", "{}").status()).isEqualTo("rejected");
		assertThat(service.recordAssessment(analysis, "extreme", "{}").status()).isEqualTo("rejected");
		assertThat(jdbc.queryForObject("select risk from analysis where id = ?", String.class, analysis)).isEqualTo("high");
	}

	@Test
	void theOfficerReportIsStoredOncePerKeyAndNeedsAStoredRisk() {
		String key = analysis + ":now:report";
		assertThat(service.notifyWildlifeOfficer(analysis, key, "Three elephants").status()).isEqualTo("rejected");
		service.recordAssessment(analysis, "high", "{}");
		assertThat(service.notifyWildlifeOfficer(analysis, key, "Three elephants").status()).isEqualTo("accepted");
		assertThat(service.notifyWildlifeOfficer(analysis, key, "Three elephants").status()).isEqualTo("accepted");
		assertThat(count("select count(*) from officer_report where analysis_id = ?")).isOne();
	}

	@Test
	void everyOutcomeIsInTheAuditLog() { // invariant 11
		service.recordAssessment(analysis, "high", "{}");
		service.sendVillageAlert(analysis, key("now", 0), Stage.NOW, DANGER);
		service.sendVillageAlert(analysis, key("now", 1), Stage.NOW, WARNING);
		assertThat(auditActions()).containsExactly("risk_recorded", "village_alert_rejected", "village_alert_accepted");
	}

}
