package com.kodland.course;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Puts the library files into Minescript's module folder and keeps them there.
 *
 * The folder is shared: Minescript writes its own modules next to ours on every
 * start, and a child can drop anything into it. So the mod only ever writes and
 * deletes the files it owns. Which ones those are is remembered in
 * {@link #REGISTRY}, written after every delivery, so that a file a later version
 * no longer carries can be removed without touching anybody else's.
 */
public final class LibraryDelivery {
	/** The list of files this mod put into the folder. */
	public static final String REGISTRY = ".kodland-files.json";

	/** kodland.py and kodland_*.py; nothing else is ever written or deleted. */
	private static final Pattern LIBRARY_NAME = Pattern.compile("kodland(_[a-z0-9_]+)?\\.py");

	private LibraryDelivery() {
	}

	public static boolean isLibraryName(String name) {
		return LIBRARY_NAME.matcher(name).matches();
	}

	/** What one delivery did, for the single line in the log. */
	public record Result(List<String> written, List<String> current, List<String> removed) {
		public String describe() {
			List<String> parts = new ArrayList<>();
			if (!written.isEmpty()) {
				parts.add("wrote " + String.join(", ", written));
			}
			if (!removed.isEmpty()) {
				parts.add("removed old " + String.join(", ", removed));
			}
			if (written.isEmpty() && removed.isEmpty()) {
				parts.add("up to date (" + String.join(", ", current) + ")");
			}
			return String.join("; ", parts);
		}
	}

	/** The library files inside the mod, by name. Anything that is not a library file is skipped. */
	public static Map<String, byte[]> readBundle(Path source) throws IOException {
		Map<String, byte[]> bundle = new TreeMap<>();
		try (Stream<Path> files = Files.list(source)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String name = file.getFileName().toString();
				if (isLibraryName(name) && Files.isRegularFile(file)) {
					bundle.put(name, Files.readAllBytes(file));
				}
			}
		}
		return bundle;
	}

	public static Result deliver(Map<String, byte[]> bundle, Path folder, String version) throws IOException {
		Files.createDirectories(folder);

		List<String> written = new ArrayList<>();
		List<String> current = new ArrayList<>();
		for (Map.Entry<String, byte[]> entry : new TreeMap<>(bundle).entrySet()) {
			Path file = folder.resolve(entry.getKey());
			if (Files.isRegularFile(file) && Arrays.equals(sha256(Files.readAllBytes(file)), sha256(entry.getValue()))) {
				current.add(entry.getKey());
			} else {
				writeWhole(file, entry.getValue());
				written.add(entry.getKey());
			}
		}

		// Only names this mod wrote before, and only library names: a registry a
		// child edited by hand cannot point the mod at anything else.
		List<String> removed = new ArrayList<>();
		for (String name : readRegistry(folder)) {
			if (!bundle.containsKey(name) && isLibraryName(name) && Files.deleteIfExists(folder.resolve(name))) {
				removed.add(name);
			}
		}

		writeRegistry(folder, version, bundle.keySet());
		return new Result(written, current, removed);
	}

	static List<String> readRegistry(Path folder) {
		Path file = folder.resolve(REGISTRY);
		List<String> names = new ArrayList<>();
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (root.isJsonObject() && root.getAsJsonObject().get("files") instanceof JsonArray list) {
				for (JsonElement item : list) {
					if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
						names.add(item.getAsString());
					}
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// No list yet, or a broken one: nothing is known to be ours, so nothing is removed.
		}
		return names;
	}

	private static void writeRegistry(Path folder, String version, Collection<String> names) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", version);
		JsonArray files = new JsonArray();
		for (String name : new TreeSet<>(names)) {
			files.add(name);
		}
		root.add("files", files);
		writeWhole(folder.resolve(REGISTRY), new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
	}

	/** Write to a side file, then move it over: a crash mid-write never leaves half a library. */
	private static void writeWhole(Path file, byte[] bytes) throws IOException {
		Path side = file.resolveSibling(file.getFileName() + ".tmp");
		Files.write(side, bytes);
		try {
			Files.move(side, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(side, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
