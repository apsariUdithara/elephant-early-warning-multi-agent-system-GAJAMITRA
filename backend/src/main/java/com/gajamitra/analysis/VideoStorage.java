package com.gajamitra.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded clips on the volume shared with the AI service. The file name is generated, never taken from the
 * client (no path traversal), and the content must look like MP4 (an ISO "ftyp" box), whatever the client claims.
 */
@Component
public class VideoStorage {

	private final Path directory;

	VideoStorage(@Value("${gajamitra.storage.videos:data/videos}") String directory) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
	}

	/** Thrown when the upload isn't an MP4 video. */
	public static class NotAVideo extends RuntimeException {

		NotAVideo() {
			super("the file is not an MP4 video");
		}

	}

	Path store(MultipartFile file) throws IOException {
		byte[] head = new byte[12];
		try (InputStream in = file.getInputStream()) {
			if (in.readNBytes(head, 0, 12) < 12 || head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p') {
				throw new NotAVideo();
			}
		}
		Files.createDirectories(directory);
		Path target = directory.resolve(UUID.randomUUID() + ".mp4");
		try (InputStream in = file.getInputStream()) {
			Files.copy(in, target);
		}
		catch (IOException | RuntimeException e) {
			Files.deleteIfExists(target); // never leave a partial clip
			throw e;
		}
		return target;
	}

}
