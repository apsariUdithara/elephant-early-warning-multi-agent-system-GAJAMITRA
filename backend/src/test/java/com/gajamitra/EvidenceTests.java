package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.gajamitra.evidence.EvidenceStore;

/** The frames the agents looked at: served only to signed-in users, from a UUID and a number, kept 30 days. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "gajamitra.storage.evidence=target/test-evidence")
@AutoConfigureMockMvc
class EvidenceTests {

	static final Path DIR = Path.of("target/test-evidence").toAbsolutePath();

	@Autowired
	MockMvc mvc;

	@Autowired
	EvidenceStore store;

	@Autowired
	JdbcTemplate jdbc;

	UUID frames(int count) throws Exception {
		UUID id = UUID.randomUUID();
		Files.createDirectories(DIR.resolve(id.toString()));
		for (int i = 0; i < count; i++) {
			Files.write(DIR.resolve(id.toString()).resolve(i + ".jpg"), new byte[] { (byte) 0xff, (byte) 0xd8, (byte) i });
		}
		return id;
	}

	@Test
	void aSignedInOfficerGetsTheFrameAsANonCachedJpeg() throws Exception {
		UUID id = frames(2);
		mvc.perform(get("/api/analyses/" + id + "/frames/1").with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
			.andExpect(content().bytes(new byte[] { (byte) 0xff, (byte) 0xd8, 1 }));
	}

	@Test
	void anonymousVisitorsGetNoFrames() throws Exception {
		UUID id = frames(1);
		mvc.perform(get("/api/analyses/" + id + "/frames/0")).andExpect(status().isUnauthorized());
	}

	@Test
	void missingOutOfRangeAndNonsenseRequestsAreNotFoundOrRefused() throws Exception {
		UUID id = frames(1);
		var officer = user("o").roles("OFFICER");
		mvc.perform(get("/api/analyses/" + id + "/frames/5").with(officer)).andExpect(status().isNotFound());
		mvc.perform(get("/api/analyses/" + id + "/frames/100").with(officer)).andExpect(status().isNotFound());
		mvc.perform(get("/api/analyses/" + id + "/frames/-1").with(officer)).andExpect(status().isNotFound());
		mvc.perform(get("/api/analyses/" + UUID.randomUUID() + "/frames/0").with(officer)).andExpect(status().isNotFound());
		// Not a UUID: it can never become a path (no traversal), it is refused before touching the disk.
		mvc.perform(get("/api/analyses/..%2F..%2Fetc/frames/0").with(officer)).andExpect(status().is4xxClientError());
		mvc.perform(get("/api/analyses/" + id + "/frames/abc").with(officer)).andExpect(status().isBadRequest());
	}

	@Test
	void theApprovalsListSaysHowManyFramesEachApprovalHas() throws Exception {
		UUID site = jdbc.queryForObject("insert into site (name) values ('Galgamuwa') returning id", UUID.class);
		UUID analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, 'v', '{0,0.5,1,0.5,0.5,0.9}') returning id""",
				UUID.class, site);
		Files.createDirectories(DIR.resolve(analysis.toString()));
		for (int i = 0; i < 3; i++) {
			Files.write(DIR.resolve(analysis.toString()).resolve(i + ".jpg"), new byte[] { 1 });
		}
		jdbc.update("insert into approval_request (analysis_id, proposed_alert, expires_at) values (?, '[]', now() + interval '2 minutes')",
				analysis);
		mvc.perform(get("/api/approvals").with(user("o").roles("OFFICER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.analysisId == '" + analysis + "')].frames", org.hamcrest.Matchers.contains(3)));
	}

	@Test
	void viewingAFrameOfARealAnalysisIsAudited() throws Exception {
		UUID site = jdbc.queryForObject("insert into site (name) values ('Hurulu') returning id", UUID.class);
		UUID analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, 'v', '{0,0.5,1,0.5,0.5,0.9}') returning id""",
				UUID.class, site);
		Files.createDirectories(DIR.resolve(analysis.toString()));
		Files.write(DIR.resolve(analysis.toString()).resolve("0.jpg"), new byte[] { 1 });
		mvc.perform(get("/api/analyses/" + analysis + "/frames/0").with(user("officer1").roles("OFFICER"))).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where analysis_id = ? and action = 'evidence_viewed'",
				Integer.class, analysis)).isEqualTo(1);
	}

	@Test
	void evidenceOlderThanThirtyDaysIsDeletedAndRecentEvidenceKept() throws Exception {
		UUID old = frames(2);
		UUID recent = frames(1);
		Files.setLastModifiedTime(DIR.resolve(old.toString()), FileTime.from(Instant.now().minus(Duration.ofDays(31))));
		assertThat(store.purge(Instant.now())).isGreaterThanOrEqualTo(1);
		assertThat(Files.exists(DIR.resolve(old.toString()))).isFalse();
		assertThat(Files.exists(DIR.resolve(recent.toString()))).isTrue();
	}

}
