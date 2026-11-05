package com.gajamitra.evidence;

import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A frame the agents looked at, for signed-in officers and admins only (/api/**). Never cached by shared caches. */
@RestController
@RequestMapping("/api/analyses/{id}/frames")
class EvidenceController {

	private final EvidenceStore store;

	EvidenceController(EvidenceStore store) {
		this.store = store;
	}

	@GetMapping("/{number}")
	ResponseEntity<Resource> frame(@PathVariable UUID id, @PathVariable int number) {
		Path file = store.frame(id, number);
		if (file == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(file));
	}

}
