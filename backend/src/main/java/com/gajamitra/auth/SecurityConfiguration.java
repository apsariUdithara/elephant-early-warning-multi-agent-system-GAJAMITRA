package com.gajamitra.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Dashboard security (plan Phase 3, Security): session login, BCrypt, roles OFFICER (view, approve) and ADMIN
 * (+ sites, subscribers). CSRF stays on with a cookie the SPA sends back as X-XSRF-TOKEN. The MCP endpoint is outside
 * this chain's login: it has its own API key ({@code mcp.McpApiKeyFilter}) and no browser session, so no CSRF.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Bean
	SecurityFilterChain api(HttpSecurity http, LoginThrottle throttle,
			@Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) throws Exception {
		var csrfCookies = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfCookies.setCookieCustomizer(c -> c.secure(secureCookies).sameSite("Lax"));
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/mcp", "/mcp/**", "/sse", "/sse/**").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/sites/**").hasRole("ADMIN")
				.requestMatchers("/api/**").hasAnyRole("OFFICER", "ADMIN")
				.anyRequest().denyAll())
			// spa(): the SPA reads the XSRF-TOKEN cookie and sends X-XSRF-TOKEN; BREACH-safe token handling.
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfCookies)
				.ignoringRequestMatchers("/mcp", "/mcp/**", "/sse", "/sse/**"))
			.addFilterBefore(throttle, UsernamePasswordAuthenticationFilter.class)
			.formLogin(login -> login.loginProcessingUrl("/api/login")
				.successHandler((request, response, authentication) -> {
					throttle.succeeded(request.getParameter("username"));
					response.setStatus(HttpStatus.NO_CONTENT.value());
				})
				.failureHandler((request, response, e) -> {
					throttle.failed(request.getParameter("username"));
					response.setStatus(HttpStatus.UNAUTHORIZED.value());
				})
				.permitAll())
			.logout(logout -> logout.logoutUrl("/api/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// An API answers 401, never a redirect to a login page.
			.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.httpBasic(basic -> basic.disable());
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService users(AppUserRepository users) {
		return username -> users.findByUsername(username)
			.map(u -> new User(u.username(), u.passwordHash(), AuthorityUtils.createAuthorityList("ROLE_" + u.role())))
			.orElseThrow(() -> new UsernameNotFoundException("unknown user"));
	}

	/**
	 * The first ADMIN, so a fresh install can log in: created from GAJAMITRA_ADMIN_USERNAME / GAJAMITRA_ADMIN_PASSWORD
	 * only when there are no users yet. Never overwrites an existing user.
	 */
	@Bean
	ApplicationRunner firstAdmin(AppUserRepository users, PasswordEncoder encoder,
			@Value("${gajamitra.admin.username:}") String username, @Value("${gajamitra.admin.password:}") String password) {
		return args -> {
			if (users.count() > 0) {
				return;
			}
			if (username.isBlank() || password.length() < 12) {
				log.warn("no users yet: set GAJAMITRA_ADMIN_USERNAME and GAJAMITRA_ADMIN_PASSWORD (12+ characters) "
						+ "to create the first admin");
				return;
			}
			users.save(new AppUser(username, encoder.encode(password), AppUser.Role.ADMIN));
			log.info("created the first admin user; remove GAJAMITRA_ADMIN_PASSWORD from the environment");
		};
	}

}
