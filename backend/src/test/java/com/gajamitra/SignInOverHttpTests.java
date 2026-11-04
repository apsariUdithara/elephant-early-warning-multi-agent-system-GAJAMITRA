package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The dashboard's real first steps over HTTP, the way a browser does them: the first call (GET /api/me) must hand it
 * the CSRF cookie, and the login POST then sends it back as a header. MockMvc can't show this (it never commits the
 * response), so this test uses a real server.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignInOverHttpTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@LocalServerPort
	int port;

	final HttpClient client = HttpClient.newHttpClient();

	HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream()
			.filter(h -> h.startsWith(name + "="))
			.map(h -> h.substring(name.length() + 1, h.indexOf(';')))
			.findFirst()
			.orElse(null);
	}

	HttpRequest.Builder login(String user, String password, String token) {
		String form = "username=" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(password, StandardCharsets.UTF_8);
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form));
		if (token != null) {
			request.header("Cookie", "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token);
		}
		return request;
	}

	@Test
	void theFirstCallGivesTheCsrfCookieAndTheLoginThenSucceedsWithIt() throws Exception {
		String user = "dash" + UUID.randomUUID();
		jdbc.update("insert into app_user (username, password_hash, role) values (?, ?, 'OFFICER')", user,
				encoder.encode("correct horse battery"));

		var first = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me")));
		assertThat(first.statusCode()).isEqualTo(401);
		String token = cookie(first, "XSRF-TOKEN");
		assertThat(token).as("Set-Cookie: %s", first.headers().allValues("Set-Cookie")).isNotNull();

		// Without the token even the right password is refused (the entry point answers 401 to an anonymous caller).
		assertThat(send(login(user, "correct horse battery", null)).statusCode()).isIn(401, 403);
		assertThat(send(login(user, "wrong password", token)).statusCode()).isEqualTo(401);
		var signedIn = send(login(user, "correct horse battery", token));
		assertThat(signedIn.statusCode()).isEqualTo(204);
		assertThat(cookie(signedIn, "JSESSIONID")).isNotNull();
	}

}
