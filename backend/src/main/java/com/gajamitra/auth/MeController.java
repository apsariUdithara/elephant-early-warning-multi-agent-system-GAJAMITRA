package com.gajamitra.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who is signed in, for the dashboard (it shows the name and decides which pages to offer). Showing or hiding a page
 * is only convenience: every endpoint still checks the role itself. No session answers 401 (security configuration).
 */
@RestController
@RequestMapping("/api/me")
class MeController {

	record MeView(String username, String role) {
	}

	private final AppUserRepository users;

	MeController(AppUserRepository users) {
		this.users = users;
	}

	@GetMapping
	ResponseEntity<MeView> me(@AuthenticationPrincipal UserDetails principal) {
		return users.findByUsername(principal.getUsername())
			.map(u -> ResponseEntity.ok(new MeView(u.username(), u.role().name())))
			.orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()); // deleted while signed in
	}

}
