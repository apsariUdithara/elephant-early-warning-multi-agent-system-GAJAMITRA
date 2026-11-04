package com.gajamitra.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Only the AI service may call the MCP endpoint: it must send the shared key (MCP_API_KEY) in the X-Api-Key header.
 * With no key configured every call is refused (fails closed). The endpoint is also kept off the public port in
 * compose (plan Phase 3, Security).
 * <p>
 * The path is matched the way Spring MVC routes it (decoded, ";params" removed), so "/mcp;x=1" or "/%6Dcp" can't slip
 * past the filter and still reach the handler. "/sse" is guarded too, in case an SSE transport is ever enabled.
 */
@Component
class McpApiKeyFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Api-Key";

	private static final Logger log = LoggerFactory.getLogger(McpApiKeyFilter.class);

	private static final UrlPathHelper PATHS = new UrlPathHelper();

	private final byte[] keyHash;

	private final String endpoint;

	McpApiKeyFilter(@Value("${gajamitra.mcp.api-key:}") String key,
			@Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
		this.keyHash = key.isBlank() ? null : sha256(key);
		this.endpoint = endpoint.toLowerCase();
		if (key.isBlank()) {
			log.warn("MCP_API_KEY is not set: every MCP call will be refused");
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = PATHS.getPathWithinApplication(request).toLowerCase();
		while (path.contains("//")) {
			path = path.replace("//", "/");
		}
		return !(guarded(path, endpoint) || guarded(path, "/sse"));
	}

	private static boolean guarded(String path, String prefix) {
		return path.equals(prefix) || path.startsWith(prefix + "/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String sent = request.getHeader(HEADER);
		// Hashes have a fixed length, so the comparison time says nothing about the key's length.
		if (keyHash == null || sent == null || !MessageDigest.isEqual(keyHash, sha256(sent))) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		chain.doFilter(request, response);
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
