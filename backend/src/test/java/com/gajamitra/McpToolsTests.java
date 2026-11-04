package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.gajamitra.alert.VillageAlert;
import com.gajamitra.mcp.GajamitraTools;

/** The MCP tools validate untrusted arguments and only the AI service (holding the key) can reach them. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "gajamitra.mcp.api-key=test-key-for-mcp")
class McpToolsTests {

	static final VillageAlert WARNING = new VillageAlert("warning", "Elephants near the fence.", "සිංහල", "தமிழ்");

	@Autowired
	GajamitraTools tools;

	@Autowired
	JdbcTemplate jdbc;

	@LocalServerPort
	int port;

	UUID site;

	UUID analysis;

	@BeforeEach
	void seed() {
		site = jdbc.queryForObject("""
				insert into site (name, villages, mode, requires_approval) values ('Galgamuwa', 'Ehetuwewa', 'LIVE', false)
				returning id""", UUID.class);
		analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, '/data/v.mp4', '{0,0.5,1,0.5,0.5,0.9}')
				returning id""", UUID.class, site);
	}

	@Test
	void theFullToolPathRecordsTheRiskAndAcceptsAnAllowedAlert() {
		assertThat(tools.recordAssessment(analysis.toString(), "high", Map.of("herd_size", 3)).status())
			.isEqualTo("recorded");
		assertThat(tools.sendVillageAlert(analysis.toString(), analysis + ":now:0", "NOW", WARNING).status())
			.isEqualTo("accepted");
		assertThat(tools.notifyWildlifeOfficer(analysis.toString(), analysis + ":now:report", "Three elephants").status())
			.isEqualTo("accepted");
	}

	@Test
	void malformedArgumentsAreRejectedNotThrown() {
		assertThat(tools.recordAssessment("not-a-uuid", "high", Map.of()).reason()).contains("UUID");
		assertThat(tools.sendVillageAlert(null, "k", "NOW", WARNING).status()).isEqualTo("rejected");
		assertThat(tools.sendVillageAlert(analysis.toString(), analysis + ":now:0", "LATER", WARNING).reason())
			.contains("stage");
		assertThat(tools.sendVillageAlert(analysis.toString(), analysis + ":now:0", "NOW", null).status())
			.isEqualTo("rejected");
		var tooLong = new VillageAlert("warning", "x".repeat(321), "සිංහල", "தமிழ்");
		assertThat(tools.sendVillageAlert(analysis.toString(), analysis + ":now:0", "NOW", tooLong).status())
			.isEqualTo("rejected");
		assertThat(tools.notifyWildlifeOfficer(analysis.toString(), analysis + ":now:report", " ").status())
			.isEqualTo("rejected");
		assertThat(jdbc.queryForObject("select count(*) from alert where analysis_id = ?", Integer.class, analysis))
			.isZero();
	}

	@Test
	void siteProceduresDefaultToRequiringApprovalForAnUnknownSite() {
		assertThat(tools.getSiteProcedures(site.toString()))
			.isEqualTo(new GajamitraTools.SiteProcedures("Galgamuwa", "Ehetuwewa", false));
		assertThat(tools.getSiteProcedures(UUID.randomUUID().toString()).requires_approval()).isTrue();
		assertThat(tools.getSiteProcedures("junk").requires_approval()).isTrue();
	}

	@Test
	void siteHistoryListsOnlyAssessedAnalyses() {
		tools.recordAssessment(analysis.toString(), "medium", Map.of());
		jdbc.update("""
				insert into analysis (site_id, video_path, boundary) values (?, '/data/w.mp4', '{0,0.5,1,0.5,0.5,0.9}')""",
				site); // not assessed yet
		assertThat(tools.getSiteHistory(site.toString(), 14))
			.singleElement()
			.satisfies(i -> assertThat(i.risk()).isEqualTo("medium"));
		assertThat(tools.getSiteHistory(site.toString(), 0)).isEmpty();
		assertThat(tools.getSiteHistory("junk", 14)).isEmpty();
	}

	int post(String key) throws Exception {
		return post("/mcp", key);
	}

	int post(String path, String key) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream");
		if (key != null) {
			request.header("X-Api-Key", key);
		}
		String initialize = """
				{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18",
				 "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}}""";
		return HttpClient.newHttpClient()
			.send(request.POST(HttpRequest.BodyPublishers.ofString(initialize)).build(),
					HttpResponse.BodyHandlers.discarding())
			.statusCode();
	}

	HttpResponse<String> rpc(String session, String body) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.header("X-Api-Key", "test-key-for-mcp");
		if (session != null) {
			request.header("Mcp-Session-Id", session);
		}
		return HttpClient.newHttpClient()
			.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void theToolNamesMatchTheAiServiceContract() throws Exception { // ai-service/gajamitra/ports.py ToolClient
		var init = rpc(null, """
				{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18",
				 "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}}""");
		String session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
		rpc(session, """
				{"jsonrpc": "2.0", "method": "notifications/initialized"}""");
		String tools = rpc(session, """
				{"jsonrpc": "2.0", "id": 2, "method": "tools/list"}""").body();
		assertThat(tools).contains("\"record_assessment\"", "\"send_village_alert\"", "\"notify_wildlife_officer\"",
				"\"get_site_procedures\"", "\"get_site_history\"");
	}

	@Test
	void theMcpEndpointNeedsTheSharedKey() throws Exception {
		assertThat(post(null)).isEqualTo(401);
		assertThat(post("wrong")).isEqualTo(401);
		assertThat(post("test-key-for-mcp")).isEqualTo(200);
	}

	@Test
	void pathTricksDontSkipTheKeyCheck() throws Exception {
		for (String path : new String[] { "/mcp;x=1", "/%6Dcp", "//mcp", "/MCP", "/mcp/", "/sse" }) {
			assertThat(post(path, null)).as(path).isEqualTo(401);
		}
	}

	@Test
	void oversizedArgumentsAreRejected() {
		assertThat(tools.recordAssessment(analysis.toString(), "high", Map.of("x", "y".repeat(20_000))).reason())
			.contains("summary");
		assertThat(tools.sendVillageAlert(analysis.toString(), "k".repeat(101), "NOW", WARNING).reason())
			.contains("idempotency_key");
	}

}
