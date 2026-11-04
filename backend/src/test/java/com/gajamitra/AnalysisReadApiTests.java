package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.gajamitra.analysis.AnalysisChanged;

/** What the dashboard reads: history, the analysis with what was sent, and live updates (plan Phase 3, REST API). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisReadApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	UUID site;

	UUID analysis;

	@BeforeEach
	void seed() {
		site = jdbc.queryForObject("insert into site (name) values ('Galgamuwa') returning id", UUID.class);
		analysis = newAnalysis("a.mp4");
	}

	UUID newAnalysis(String video) {
		return jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, ?, '{0,0.5,1,0.5,0.5,0.9}') returning id""",
				UUID.class, site, "/data/" + video);
	}

	@Test
	void theHistoryListsTheSitesAnalysesNewestFirstWithoutStoragePaths() throws Exception {
		UUID second = newAnalysis("b.mp4");
		mvc.perform(get("/api/analyses").param("siteId", site.toString()).with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(second.toString()))
			.andExpect(jsonPath("$[0].videoPath").doesNotExist());
		mvc.perform(get("/api/analyses").param("siteId", UUID.randomUUID().toString()).with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void theDetailShowsTheOutcomeWhatWasSentAndTheOfficerReport() throws Exception {
		jdbc.update("update analysis set status = 'COMPLETED', risk = 'high', result = ?::jsonb where id = ?",
				"{\"herd_size\": 8, \"approval\": \"approve\", \"notes\": [\"verifier: approve\"]}", analysis);
		UUID alert = jdbc.queryForObject("""
				insert into alert (analysis_id, idempotency_key, stage, severity, text_en, text_si, text_ta, shadow)
				values (?, ?, 'NOW', 'warning', 'Elephant warning', 'අලි', 'யானை', true) returning id""", UUID.class,
				analysis, analysis + ":now:0");
		UUID villager = jdbc.queryForObject(
				"insert into subscriber (site_id, phone, language) values (?, ?, 'SI') returning id", UUID.class, site,
				"+9477" + (1_000_000 + System.nanoTime() % 8_000_000));
		jdbc.update("insert into alert_delivery (alert_id, subscriber_id, status) values (?, ?, 'SKIPPED_SHADOW')", alert,
				villager);
		jdbc.update("insert into officer_report (analysis_id, idempotency_key, report) values (?, ?, 'Eight elephants')",
				analysis, analysis + ":now:report");
		mvc.perform(get("/api/analyses/" + analysis).with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.result.herd_size").value(8))
			.andExpect(jsonPath("$.result.notes[0]").value("verifier: approve"))
			.andExpect(jsonPath("$.alerts[0].severity").value("warning"))
			.andExpect(jsonPath("$.alerts[0].si").value("අලි"))
			.andExpect(jsonPath("$.alerts[0].shadow").value(true))
			.andExpect(jsonPath("$.alerts[0].deliveries.SKIPPED_SHADOW").value(1))
			.andExpect(jsonPath("$.officerReport").value("Eight elephants"))
			.andExpect(jsonPath("$.videoPath").doesNotExist())
			.andExpect(jsonPath("$.alerts[0].phone").doesNotExist());
		mvc.perform(get("/api/analyses/" + UUID.randomUUID()).with(user("o").roles("OFFICER")))
			.andExpect(status().isNotFound());
	}

	@Test
	void theLiveStreamNeedsALoginAndAnExistingAnalysis() throws Exception {
		mvc.perform(get("/api/analyses/" + analysis + "/events")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/analyses/" + UUID.randomUUID() + "/events").with(user("o").roles("OFFICER")))
			.andExpect(status().isNotFound());
	}

	@Test
	void aCommittedChangeIsPushedToAnOpenStreamButARolledBackOneIsNot() throws Exception {
		MvcResult stream = mvc.perform(get("/api/analyses/" + analysis + "/events").with(user("o").roles("OFFICER")))
			.andExpect(request().asyncStarted())
			.andReturn();
		transaction.executeWithoutResult(s -> events.publishEvent(new AnalysisChanged(analysis, "approval")));
		transaction.executeWithoutResult(s -> {
			events.publishEvent(new AnalysisChanged(analysis, "never-committed"));
			s.setRollbackOnly();
		});
		String body = stream.getResponse().getContentAsString();
		assertThat(body).contains("event:analysis").contains("\"kind\":\"subscribed\"").contains("\"kind\":\"approval\"");
		assertThat(body).doesNotContain("never-committed");
	}

	@Test
	void anUnreadableStoredResultDoesNotHideTheAnalysis() throws Exception {
		jdbc.update("update analysis set status = 'COMPLETED', risk = 'high', result = '\"not an object\"'::jsonb where id = ?",
				analysis);
		mvc.perform(get("/api/analyses/" + analysis).with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.risk").value("high"))
			.andExpect(jsonPath("$.result.unparseable").value(true));
	}

	@Test
	void aLongFailureReasonIsShortenedAndSeveralAlertsNeedOneDeliveryQuery() throws Exception {
		jdbc.update("update analysis set status = 'FAILED', failure_reason = ? where id = ?", "x".repeat(900), analysis);
		for (String stage : new String[] { "now", "escalation" }) {
			jdbc.update("""
					insert into alert (analysis_id, idempotency_key, stage, severity, text_en, text_si, text_ta, shadow)
					values (?, ?, ?, 'warning', 'en', 'si', 'ta', true)""", analysis, analysis + ":" + stage + ":0",
					stage.toUpperCase());
		}
		mvc.perform(get("/api/analyses/" + analysis).with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.failureReason").value("x".repeat(500)))
			.andExpect(jsonPath("$.alerts.length()").value(2));
	}

	@Autowired
	com.gajamitra.analysis.AnalysisEvents liveEvents;

	@Test
	void aNewStreamIsRefusedPastThePerAnalysisLimit() throws Exception {
		for (int i = 0; i < 20; i++) {
			mvc.perform(get("/api/analyses/" + analysis + "/events").with(user("o").roles("OFFICER")))
				.andExpect(request().asyncStarted());
		}
		mvc.perform(get("/api/analyses/" + analysis + "/events").with(user("o").roles("OFFICER")))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void theHeartbeatPingsOpenStreams() throws Exception {
		MvcResult stream = mvc.perform(get("/api/analyses/" + analysis + "/events").with(user("o").roles("OFFICER")))
			.andExpect(request().asyncStarted())
			.andReturn();
		liveEvents.heartbeat();
		assertThat(stream.getResponse().getContentAsString()).contains(":ping");
	}

	@Test
	void eachAnalysisOnlyHearsItsOwnChanges() throws Exception {
		MvcResult stream = mvc.perform(get("/api/analyses/" + analysis + "/events").with(user("o").roles("OFFICER")))
			.andExpect(request().asyncStarted())
			.andReturn();
		UUID other = newAnalysis("c.mp4");
		transaction.executeWithoutResult(s -> events.publishEvent(new AnalysisChanged(other, "alert")));
		assertThat(stream.getResponse().getContentAsString()).doesNotContain("\"kind\":\"alert\"");
	}

}
