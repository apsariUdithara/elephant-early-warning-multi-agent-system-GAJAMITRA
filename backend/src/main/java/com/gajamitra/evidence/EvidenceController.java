package com.gajamitra.evidence;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gajamitra.audit.AuditEntry.Actor;
import com.gajamitra.audit.AuditLog;

/** A frame the agents looked at, for signed-in officers and admins only (/api/**). Never cached by shared caches. */
@RestController
@RequestMapping("/api/analyses/{id}/frames")
class EvidenceController {

	private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);

	private final EvidenceStore store;
	private final AuditLog audit;

	EvidenceController(EvidenceStore store, AuditLog audit) {
		this.store = store;
		this.audit = audit;
	}

	@GetMapping("/{number}")
	ResponseEntity<Resource> frame(@PathVariable UUID id, @PathVariable int number, Authentication who) {
		Path file = store.frame(id, number);
		if (file == null) {
			return ResponseEntity.notFound().build();
		}
		try { // who looked at footage-derived evidence; a failed audit write is logged and does not hide the picture
			audit.record(id, Actor.OFFICER, "evidence_viewed", Map.of("frame", number, "user", who.getName()));
		}
		catch (RuntimeException e) {
			log.warn("could not audit the evidence view for analysis {}", id, e);
		}
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(file));
	}

}
