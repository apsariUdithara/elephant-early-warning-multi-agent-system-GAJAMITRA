package com.gajamitra.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The frames the AI service's agents looked at, kept so an officer can see the evidence before deciding (plan Phase 5).
 * The worker writes them to a volume shared with Spring; Spring only reads and deletes. Paths are built from a UUID and a
 * small number, never from client text, and kept 30 days like the footage (plan Phase 3, retention).
 */
@Component
public class EvidenceStore {

	/** Evidence is kept as long as the footage it came from. */
	static final Duration KEEP = Duration.ofDays(30);

	/** The AI service saves a handful of frames per analysis; anything above this is not a frame. */
	static final int MAX_FRAMES = 99;

	private static final Logger log = LoggerFactory.getLogger(EvidenceStore.class);

	private final Path directory;

	EvidenceStore(@Value("${gajamitra.storage.evidence:data/evidence}") String directory) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
	}

	/** How many frames are stored for an analysis. */
	public int count(UUID analysisId) {
		Path folder = directory.resolve(analysisId.toString());
		if (!Files.isDirectory(folder)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(folder)) {
			return (int) files.filter(f -> f.getFileName().toString().matches("\\d{1,2}\\.jpg")).count();
		}
		catch (IOException e) {
			return 0;
		}
	}

	/** The frame file, or null if the number is out of range or the frame does not exist. */
	Path frame(UUID analysisId, int number) {
		if (number < 0 || number > MAX_FRAMES) {
			return null;
		}
		Path file = directory.resolve(analysisId.toString()).resolve(number + ".jpg").normalize();
		return file.startsWith(directory) && Files.isRegularFile(file) ? file : null;
	}

	/** Daily: delete evidence older than 30 days (retention, with the footage). */
	@Scheduled(cron = "0 30 3 * * *")
	void scheduledPurge() {
		purge(Instant.now());
	}

	/** Deletes every analysis folder last changed before {@code now - 30 days}. Returns how many were deleted. */
	public int purge(Instant now) {
		if (!Files.isDirectory(directory)) {
			return 0;
		}
		int deleted = 0;
		try (Stream<Path> folders = Files.list(directory)) {
			for (Path folder : (Iterable<Path>) folders.filter(Files::isDirectory)::iterator) {
				if (Files.getLastModifiedTime(folder).toInstant().isBefore(now.minus(KEEP)) && delete(folder)) {
					deleted++;
				}
			}
		}
		catch (IOException e) {
			log.warn("could not scan the evidence folder for old frames", e);
		}
		return deleted;
	}

	private boolean delete(Path folder) {
		try (Stream<Path> files = Files.walk(folder)) {
			for (Path p : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator) {
				Files.delete(p);
			}
			return true;
		}
		catch (IOException e) {
			log.warn("could not delete old evidence {}", folder.getFileName(), e);
			return false;
		}
	}

}
