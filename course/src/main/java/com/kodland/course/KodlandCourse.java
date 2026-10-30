package com.kodland.course;

import java.nio.file.Path;
import java.nio.file.Paths;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers the course library to Minescript on every start of the game.
 *
 * Every start, not once: a child can delete or change the file in the explorer,
 * and the next start puts it back. A file that is already right is only read.
 */
public final class KodlandCourse implements ClientModInitializer {
	public static final String MOD_ID = "kodland_course";
	private static final Logger LOG = LoggerFactory.getLogger("kodland-course");

	/** The library inside this jar; `pyj/` in the sources. */
	private static final String BUNDLE = "kodland/pyj";

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		ModContainer mod = loader.getModContainer(MOD_ID).orElseThrow();
		String version = mod.getMetadata().getVersion().getFriendlyString();

		// Without Minescript nothing would ever import the library, and a
		// minescript/ folder of ours would only confuse.
		if (!loader.isModLoaded("minescript")) {
			LOG.info("kodland library {}: Minescript is not in this game, nothing to do", version);
			return;
		}

		// The same folder Minescript itself resolves: relative to the working
		// directory, which is the game folder.
		Path folder = Paths.get("minescript", "system", "pyj");
		try {
			Path source = mod.findPath(BUNDLE).orElseThrow(() -> new IllegalStateException(BUNDLE + " is not in the jar"));
			LibraryDelivery.Result result = LibraryDelivery.deliver(LibraryDelivery.readBundle(source), folder, version);
			LOG.info("kodland library {} in {}: {}", version, folder, result.describe());
		} catch (Exception e) {
			// The game goes on; programs that import kodland will say it is missing.
			LOG.warn("kodland library {}: could not write to {}: {}", version, folder, e.toString());
		}
	}
}
