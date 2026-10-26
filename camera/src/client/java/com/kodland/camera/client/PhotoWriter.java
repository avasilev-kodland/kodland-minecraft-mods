package com.kodland.camera.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.kodland.camera.KodlandCamera;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a photo into the inbox the launcher picks up (contract §3.1).
 * The PNG goes first and the JSON last, each through a temp file and a rename:
 * the JSON is the "ready" mark, so the launcher never sees half a photo.
 * Runs on its own thread - encoding a PNG must not stall a frame.
 */
final class PhotoWriter {
	private static final Logger LOG = LoggerFactory.getLogger(KodlandCamera.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Kodland Camera writer");
		thread.setDaemon(true);
		return thread;
	});

	private PhotoWriter() {
	}

	static Path inbox() {
		return FabricLoader.getInstance().getGameDir().resolve("kodland-photos").resolve("inbox");
	}

	/** Takes ownership of {@code photo} and closes it when done. Completes with the PNG path. */
	static CompletableFuture<Path> save(NativeImage photo, PhotoMeta meta) {
		return CompletableFuture.supplyAsync(() -> {
			try (photo) {
				Path dir = inbox();
				Files.createDirectories(dir);
				Path png = dir.resolve(meta.id() + ".png");
				Path json = dir.resolve(meta.id() + ".json");

				Path pngTmp = dir.resolve(meta.id() + ".png.tmp");
				photo.writeToFile(pngTmp);
				move(pngTmp, png);

				Path jsonTmp = dir.resolve(meta.id() + ".json.tmp");
				Files.writeString(jsonTmp, GSON.toJson(meta.toJson(photo.getWidth(), photo.getHeight())), StandardCharsets.UTF_8);
				move(jsonTmp, json);

				LOG.info("Photo saved: {}", png);
				return png;
			} catch (IOException e) {
				LOG.error("Could not save photo {}", meta.id(), e);
				throw new RuntimeException(e);
			}
		}, IO);
	}

	private static void move(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
