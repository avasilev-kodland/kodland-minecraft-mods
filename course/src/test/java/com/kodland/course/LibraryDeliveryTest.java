package com.kodland.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryDeliveryTest {
	@TempDir
	Path game;

	private Path folder() {
		return game.resolve("minescript/system/pyj");
	}

	private static Map<String, byte[]> bundle(String... nameThenText) {
		Map<String, byte[]> files = new TreeMap<>();
		for (int i = 0; i < nameThenText.length; i += 2) {
			files.put(nameThenText[i], nameThenText[i + 1].getBytes(StandardCharsets.UTF_8));
		}
		return files;
	}

	private String read(String name) throws IOException {
		return Files.readString(folder().resolve(name));
	}

	@Test
	void writesTheLibraryIntoAFolderThatIsNotThereYet() throws IOException {
		var result = LibraryDelivery.deliver(bundle("kodland.py", "VERSION = \"0.1.0\""), folder(), "0.1.0");

		assertEquals(List.of("kodland.py"), result.written());
		assertEquals("VERSION = \"0.1.0\"", read("kodland.py"));
		assertEquals("wrote kodland.py", result.describe());
	}

	@Test
	void onlyReadsAFileThatIsAlreadyRight() throws IOException {
		LibraryDelivery.deliver(bundle("kodland.py", "a"), folder(), "0.1.0");
		var again = LibraryDelivery.deliver(bundle("kodland.py", "a"), folder(), "0.1.0");

		assertTrue(again.written().isEmpty());
		assertEquals("up to date (kodland.py)", again.describe());
	}

	@Test
	void putsBackALibraryTheChildDeletedOrChanged() throws IOException {
		LibraryDelivery.deliver(bundle("kodland.py", "right"), folder(), "0.1.0");
		Files.writeString(folder().resolve("kodland.py"), "broken");
		assertEquals(List.of("kodland.py"), LibraryDelivery.deliver(bundle("kodland.py", "right"), folder(), "0.1.0").written());
		assertEquals("right", read("kodland.py"));

		Files.delete(folder().resolve("kodland.py"));
		LibraryDelivery.deliver(bundle("kodland.py", "right"), folder(), "0.1.0");
		assertEquals("right", read("kodland.py"));
	}

	@Test
	void removesWhatAnOlderVersionBroughtAndLeavesEverythingElse() throws IOException {
		LibraryDelivery.deliver(bundle("kodland.py", "1", "kodland_old.py", "old"), folder(), "0.1.0");
		Files.writeString(folder().resolve("minescript.py"), "Minescript's own");
		Files.writeString(folder().resolve("kodland_mine.py"), "somebody else's");

		var result = LibraryDelivery.deliver(bundle("kodland.py", "2"), folder(), "0.2.0");

		assertEquals(List.of("kodland_old.py"), result.removed());
		assertFalse(Files.exists(folder().resolve("kodland_old.py")));
		assertEquals("Minescript's own", read("minescript.py"));
		assertEquals("somebody else's", read("kodland_mine.py"));
		assertEquals("wrote kodland.py; removed old kodland_old.py", result.describe());
	}

	@Test
	void aRegistryEditedByHandCannotDeleteOtherFiles() throws IOException {
		Files.createDirectories(folder());
		Files.writeString(folder().resolve("minescript.py"), "Minescript's own");
		Files.writeString(game.resolve("minescript/config.txt"), "config");
		Files.writeString(folder().resolve(LibraryDelivery.REGISTRY),
				"{\"files\":[\"minescript.py\",\"../../config.txt\",\"kodland.py/../minescript.py\"]}");

		var result = LibraryDelivery.deliver(bundle("kodland.py", "1"), folder(), "0.1.0");

		assertTrue(result.removed().isEmpty());
		assertTrue(Files.exists(folder().resolve("minescript.py")));
		assertTrue(Files.exists(game.resolve("minescript/config.txt")));
	}

	@Test
	void aBrokenRegistryRemovesNothing() throws IOException {
		Files.createDirectories(folder());
		Files.writeString(folder().resolve("kodland_old.py"), "old");
		Files.writeString(folder().resolve(LibraryDelivery.REGISTRY), "{ not json");

		var result = LibraryDelivery.deliver(bundle("kodland.py", "1"), folder(), "0.1.0");

		assertTrue(result.removed().isEmpty());
		assertTrue(Files.exists(folder().resolve("kodland_old.py")));
		assertTrue(read(LibraryDelivery.REGISTRY).contains("\"kodland.py\""));
	}

	@Test
	void readsOnlyLibraryFilesFromTheBundle() throws IOException {
		Path source = Files.createDirectories(game.resolve("bundle"));
		Files.writeString(source.resolve("kodland.py"), "lib");
		Files.writeString(source.resolve("kodland_music.py"), "music");
		Files.writeString(source.resolve("sys.py"), "not ours");
		Files.writeString(source.resolve(".DS_Store"), "mac");

		assertEquals(List.of("kodland.py", "kodland_music.py"), List.copyOf(LibraryDelivery.readBundle(source).keySet()));
	}
}
