package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Site and villager administration: ADMIN only, validated, audited, and safe for in-flight SMS. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SiteApiTests {

	static final String SITE = """
			{"name": "Galgamuwa", "villages": "Ehetuwewa", "officerContact": "DWC Galgamuwa",
			 "requiresApproval": true, "approvalTimeoutS": 120}""";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON);
	}

	UUID createSite() throws Exception {
		String body = mvc.perform(asAdmin(post("/api/sites")).content(SITE))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.mode").value("SHADOW"))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
	}

	UUID addVillager(UUID site, String phone) throws Exception {
		String body = mvc.perform(asAdmin(post("/api/sites/" + site + "/subscribers"))
			.content("{\"phone\": \"" + phone + "\", \"language\": \"SI\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
	}

	String phone() {
		return "+9477" + (1_000_000 + System.nanoTime() % 8_000_000);
	}

	@Test
	void onlyAnAdminManagesSites() throws Exception {
		mvc.perform(post("/api/sites").with(user("o").roles("OFFICER")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(SITE)).andExpect(status().isForbidden());
		mvc.perform(get("/api/sites")).andExpect(status().isUnauthorized());
	}

	@Test
	void aNewSiteStartsInShadowModeAndGoingLiveIsAudited() throws Exception {
		UUID site = createSite();
		mvc.perform(asAdmin(put("/api/sites/" + site + "/mode")).content("{\"mode\": \"LIVE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("LIVE"));
		assertThat(jdbc.queryForObject("select count(1) from audit_log where action = 'site_mode_live' and payload->>'site_id' = ?",
				Integer.class, site.toString())).isOne();
	}

	@Test
	void anApprovalTimeoutMustStayShort() throws Exception { // invariant 9: an escalation can't be held for long
		mvc.perform(asAdmin(post("/api/sites")).content(SITE.replace("120", "86400"))).andExpect(status().isBadRequest());
	}

	@Test
	void villagerPhonesMustBeInternationalAndUnique() throws Exception {
		UUID site = createSite();
		String phone = phone();
		addVillager(site, phone);
		mvc.perform(asAdmin(post("/api/sites/" + site + "/subscribers"))
			.content("{\"phone\": \"" + phone + "\", \"language\": \"SI\"}")).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(post("/api/sites/" + site + "/subscribers"))
			.content("{\"phone\": \"0771234567\", \"language\": \"TA\"}")).andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("select payload->>'phone' from audit_log where action = 'subscriber_added' "
				+ "and payload->>'site_id' = ?", String.class, site.toString())).contains("*****");
	}

	@Test
	void removingAVillagerKeepsTheirDeliveriesAnonymisedButWaitsForInFlightSms() throws Exception {
		UUID site = createSite();
		UUID villager = addVillager(site, phone());
		UUID analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, 'v', '{0,0.5,1,0.5,0.5,0.9}') returning id""",
				UUID.class, site);
		UUID alert = jdbc.queryForObject("""
				insert into alert (analysis_id, idempotency_key, stage, severity, text_en, text_si, text_ta, shadow)
				values (?, ?, 'NOW', 'warning', 'en', 'si', 'ta', false) returning id""", UUID.class, analysis,
				analysis + ":now:0");
		jdbc.update("insert into alert_delivery (alert_id, subscriber_id, status) values (?, ?, 'SENDING')", alert, villager);
		mvc.perform(asAdmin(delete("/api/sites/" + site + "/subscribers/" + villager))).andExpect(status().isBadRequest());
		jdbc.update("update alert_delivery set status = 'FAILED', attempts = 1 where alert_id = ?", alert); // retry due
		mvc.perform(asAdmin(delete("/api/sites/" + site + "/subscribers/" + villager))).andExpect(status().isBadRequest());
		jdbc.update("update alert_delivery set status = 'SENT' where alert_id = ?", alert);
		mvc.perform(asAdmin(delete("/api/sites/" + site + "/subscribers/" + villager))).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select subscriber_id from alert_delivery where alert_id = ?", UUID.class, alert))
			.isNull();
	}

}
