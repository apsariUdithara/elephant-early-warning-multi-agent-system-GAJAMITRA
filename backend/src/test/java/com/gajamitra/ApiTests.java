package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The dashboard API: who may call what, uploads, and officer decisions (plan Phase 3, REST API and Security). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "gajamitra.storage.videos=target/test-videos")
@AutoConfigureMockMvc
class ApiTests {

	/** The first 12 bytes of an MP4: size, then the "ftyp" box. */
	static final byte[] MP4 = { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0 };

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	UUID site;

	String officer;

	@BeforeEach
	void seed() {
		site = jdbc.queryForObject("insert into site (name) values ('Galgamuwa') returning id", UUID.class);
		officer = "officer" + System.nanoTime();
		jdbc.update("insert into app_user (username, password_hash, role) values (?, ?, 'OFFICER')", officer,
				encoder.encode("correct horse battery"));
	}

	MockMultipartFile video(byte[] content) {
		return new MockMultipartFile("video", "clip.mp4", "video/mp4", content);
	}

	@Test
	void theApiNeedsALoginAndAnswers401NotARedirect() throws Exception {
		mvc.perform(get("/api/approvals")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void anOfficerLogsInWithTheirPassword() throws Exception {
		mvc.perform(post("/api/login").with(csrf()).param("username", officer).param("password", "correct horse battery"))
			.andExpect(status().isNoContent());
		mvc.perform(post("/api/login").with(csrf()).param("username", officer).param("password", "wrong"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void repeatedWrongPasswordsLockTheUsernameEvenForTheRightPassword() throws Exception {
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/api/login").with(csrf()).param("username", officer).param("password", "guess" + i))
				.andExpect(status().isUnauthorized());
		}
		mvc.perform(post("/api/login").with(csrf()).param("username", officer).param("password", "correct horse battery"))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void meTellsTheDashboardWhoIsSignedInAndNobodyAnswers401() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").with(user(officer).roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(officer))
			.andExpect(jsonPath("$.role").value("OFFICER"));
		mvc.perform(get("/api/me").with(user("deleted-user").roles("OFFICER"))).andExpect(status().isUnauthorized());
	}

	@Test
	void writesNeedTheCsrfToken() throws Exception {
		mvc.perform(multipart("/api/analyses").file(video(MP4)).param("siteId", site.toString())
			.param("boundary", "0,0.5,1,0.5,0.5,0.9").with(user(officer).roles("OFFICER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void anUploadIsQueuedAndAnswered202() throws Exception {
		String body = mvc.perform(multipart("/api/analyses").file(video(MP4)).param("siteId", site.toString())
			.param("boundary", "0,0.5,1,0.5,0.5,0.9").with(user(officer).roles("OFFICER")).with(csrf()))
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		UUID id = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
		assertThat(jdbc.queryForObject("select status from analysis where id = ?", String.class, id)).isEqualTo("QUEUED");
		mvc.perform(get("/api/analyses/" + id).with(user(officer).roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("QUEUED"))
			.andExpect(jsonPath("$.videoPath").doesNotExist());
	}

	@Test
	void anUploadThatIsntAnMp4OrHasABadBoundaryIsRefused() throws Exception {
		mvc.perform(multipart("/api/analyses").file(video("<html>not a video".getBytes())).param("siteId", site.toString())
			.param("boundary", "0,0.5,1,0.5,0.5,0.9").with(user(officer).roles("OFFICER")).with(csrf()))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(multipart("/api/analyses").file(video(MP4)).param("siteId", site.toString())
			.param("boundary", "0,0.5,2").with(user(officer).roles("OFFICER")).with(csrf()))
			.andExpect(status().isBadRequest());
		mvc.perform(multipart("/api/analyses").file(video(MP4)).param("siteId", UUID.randomUUID().toString())
			.param("boundary", "0,0.5,1,0.5,0.5,0.9").with(user(officer).roles("OFFICER")).with(csrf()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void anOfficerDecidesOncePendingApprovalsThen409() throws Exception { // invariant 9: conditional decision
		UUID analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary, status) values (?, 'v', '{0,0.5,1,0.5,0.5,0.9}',
				'AWAITING_APPROVAL') returning id""", UUID.class, site);
		UUID approval = jdbc.queryForObject("""
				insert into approval_request (analysis_id, proposed_alert, expires_at)
				values (?, '[]', now() + interval '2 minutes') returning id""", UUID.class, analysis);
		mvc.perform(get("/api/approvals").with(user(officer).roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + approval + "')]").exists());
		String decision = "{\"decision\": \"APPROVE\", \"note\": \"herd at the fence\"}";
		mvc.perform(post("/api/approvals/" + approval).contentType(MediaType.APPLICATION_JSON).content(decision)
			.with(user(officer).roles("OFFICER")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(post("/api/approvals/" + approval).contentType(MediaType.APPLICATION_JSON).content(decision)
			.with(user(officer).roles("OFFICER")).with(csrf()))
			.andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("""
				select u.username from approval_request a join app_user u on u.id = a.decided_by where a.id = ?""",
				String.class, approval)).isEqualTo(officer);
	}

	@Test
	void theMcpEndpointIsNotBehindTheLoginButStillNeedsItsKey() throws Exception {
		mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
	}

}
