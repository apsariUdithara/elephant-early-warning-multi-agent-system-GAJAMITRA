package com.gajamitra.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Login brute-force protection: after {@link #MAX_FAILURES} wrong passwords for a username, that username is locked
 * for {@link #LOCK_FOR} (429), whatever password is sent. Same answer for unknown usernames, so it doesn't reveal
 * which exist.
 */
// ponytail: in memory, one backend instance; a restart clears it. Move to the database if the backend is scaled out.
@Component
class LoginThrottle extends OncePerRequestFilter {

	static final int MAX_FAILURES = 5;

	static final Duration LOCK_FOR = Duration.ofMinutes(15);

	private record Failures(int count, Instant last) {
	}

	private final ConcurrentHashMap<String, Failures> failures = new ConcurrentHashMap<>();

	private final Clock clock;

	LoginThrottle(Clock clock) {
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !("POST".equals(request.getMethod()) && "/api/login".equals(path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (locked(request.getParameter("username"))) {
			response.setStatus(429);
			return;
		}
		chain.doFilter(request, response);
	}

	boolean locked(String username) {
		Failures f = failures.get(key(username));
		if (f == null || f.count() < MAX_FAILURES) {
			return false;
		}
		if (clock.instant().isAfter(f.last().plus(LOCK_FOR))) {
			failures.remove(key(username));
			return false;
		}
		return true;
	}

	void failed(String username) {
		failures.merge(key(username), new Failures(1, clock.instant()),
				(old, one) -> new Failures(old.count() + 1, one.last()));
	}

	void succeeded(String username) {
		failures.remove(key(username));
	}

	private static String key(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

}
