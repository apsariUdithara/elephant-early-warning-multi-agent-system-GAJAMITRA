package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The database itself enforces the rules the safety invariants rely on (plan Phase 3 data model).
 * Runs against PostgreSQL in a container after the Flyway migrations; skipped locally without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaConstraintsTests {

	@Autowired
	JdbcTemplate jdbc;

	UUID site;
	UUID analysis;
	UUID subscriber;

	@BeforeEach
	void seed() {
		site = jdbc.queryForObject("insert into site (name) values ('Galgamuwa') returning id", UUID.class);
		subscriber = jdbc.queryForObject(
				"insert into subscriber (site_id, phone, language) values (?, ?, 'SI') returning id", UUID.class,
				site, "+9470" + System.nanoTime() % 10_000_000);
		analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, '/data/videos/c.mp4', '{0,0.5,1,0.5,0.5,0.9}')
				returning id""", UUID.class, site);
	}

	UUID alert(String key, String severity) {
		return jdbc.queryForObject("""
				insert into alert (analysis_id, idempotency_key, stage, severity, text_en, text_si, text_ta, shadow)
				values (?, ?, 'NOW', ?, 'en', 'si', 'ta', true) returning id""", UUID.class, analysis, key, severity);
	}

	@Test
	void newSitesRequireApprovalWithATwoMinuteTimeoutAndStartInShadowMode() {
		var row = jdbc.queryForMap("select mode, requires_approval, approval_timeout_s from site where id = ?", site);
		assertThat(row).containsEntry("mode", "SHADOW").containsEntry("requires_approval", true)
				.containsEntry("approval_timeout_s", 120);
	}

	@Test
	void alertIdempotencyKeyIsUnique() {
		alert(analysis + ":now:0", "warning");
		assertThatThrownBy(() -> alert(analysis + ":now:0", "warning")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void oneDeliveryPerAlertAndSubscriber() {
		UUID a = alert(analysis + ":now:0", "warning");
		String sql = "insert into alert_delivery (alert_id, subscriber_id) values (?, ?)";
		jdbc.update(sql, a, subscriber);
		assertThatThrownBy(() -> jdbc.update(sql, a, subscriber)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void officerReportIdempotencyKeyIsUnique() {
		String sql = "insert into officer_report (analysis_id, idempotency_key, report) values (?, ?, 'r')";
		jdbc.update(sql, analysis, analysis + ":now:report");
		assertThatThrownBy(() -> jdbc.update(sql, analysis, analysis + ":now:report"))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void onlyKnownRiskAndSeverityValuesAreStored() {
		assertThatThrownBy(() -> jdbc.update("update analysis set risk = 'unknown' where id = ?", analysis))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> alert(analysis + ":now:1", "emergency"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void alertMustBelongToAnExistingAnalysis() {
		UUID missing = UUID.randomUUID();
		assertThatThrownBy(() -> jdbc.update("""
				insert into alert (analysis_id, idempotency_key, stage, severity, text_en, text_si, text_ta, shadow)
				values (?, ?, 'NOW', 'warning', 'en', 'si', 'ta', true)""", missing, missing + ":now:0"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void boundaryNeedsSixNumbers() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into analysis (site_id, video_path, boundary) values (?, 'v', '{0,1}')", site))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void onlyOneApprovalCanBePendingPerAnalysis() {
		String sql = """
				insert into approval_request (analysis_id, proposed_alert, expires_at)
				values (?, '{}'::jsonb, now() + interval '2 minutes')""";
		jdbc.update(sql, analysis);
		assertThatThrownBy(() -> jdbc.update(sql, analysis)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void decidedApprovalsRecordWhenAndPendingOnesDont() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into approval_request (analysis_id, proposed_alert, expires_at, status)
				values (?, '{}'::jsonb, now(), 'TIMED_OUT')""", analysis))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anIdempotencyKeyMustBelongToItsAnalysis() {
		assertThatThrownBy(() -> alert(UUID.randomUUID() + ":now:0", "warning"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(
				"insert into officer_report (analysis_id, idempotency_key, report) values (?, ?, 'r')", analysis,
				UUID.randomUUID() + ":now:report")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAVillagerKeepsTheirDeliveryRecordWithoutTheLink() {
		UUID a = alert(analysis + ":now:0", "warning");
		jdbc.update("insert into alert_delivery (alert_id, subscriber_id, status) values (?, ?, 'SENT')", a, subscriber);
		jdbc.update("delete from subscriber where id = ?", subscriber);
		var row = jdbc.queryForMap("select subscriber_id, status from alert_delivery where alert_id = ?", a);
		assertThat(row.get("subscriber_id")).isNull();
		assertThat(row).containsEntry("status", "SENT");
	}

	@Test
	void footageIsPurgedTogetherWithItsDeletionTime() {
		jdbc.update("update analysis set video_path = null, video_deleted_at = now() where id = ?", analysis);
		assertThatThrownBy(() -> jdbc.update("update analysis set video_deleted_at = null where id = ?", analysis))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(
				"insert into analysis (site_id, boundary) values (?, '{0,0.5,1,0.5,0.5,0.9}')", site))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anOfficerDecisionNamesTheOfficerAndANewApprovalCanFollowIt() {
		UUID officer = jdbc.queryForObject(
				"insert into app_user (username, password_hash, role) values (?, 'h', 'OFFICER') returning id",
				UUID.class, "officer" + System.nanoTime());
		String pending = """
				insert into approval_request (analysis_id, proposed_alert, expires_at)
				values (?, '{}'::jsonb, now() + interval '2 minutes') returning id""";
		UUID first = jdbc.queryForObject(pending, UUID.class, analysis);
		assertThatThrownBy(() -> jdbc.update(
				"update approval_request set status = 'APPROVED', decided_at = now() where id = ?", first))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("update approval_request set status = 'APPROVED', decided_at = now(), decided_by = ? where id = ?",
				officer, first);
		assertThat(jdbc.queryForObject(pending, UUID.class, analysis)).isNotNull();
	}

	@Test
	void eachEventIdIsProcessedOnce() {
		UUID event = UUID.randomUUID();
		jdbc.update("insert into processed_event (event_id) values (?)", event);
		assertThatThrownBy(() -> jdbc.update("insert into processed_event (event_id) values (?)", event))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
