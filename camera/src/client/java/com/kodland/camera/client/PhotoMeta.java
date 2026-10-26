package com.kodland.camera.client;

import com.google.gson.JsonObject;
import com.kodland.camera.KodlandCamera;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where and when a photo was taken - read at the moment of the shot, not at the moment
 * of saving, so a child who walks away from the polaroid still gets the right place.
 * The JSON shape is contract §3.1 version 1; the launcher reads it.
 */
record PhotoMeta(
		String id,
		String takenAt,
		String mc,
		String mod,
		String worldName,
		String worldFolder,
		String dimension,
		String biome,
		int x,
		int y,
		int z,
		float yaw,
		float pitch) {

	static final int CONTRACT_VERSION = 1;
	private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	static PhotoMeta capture(Minecraft mc) {
		OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		String id = now.format(ID_TIME) + "-" + String.format("%06x", ThreadLocalRandom.current().nextInt(0x1000000));

		LocalPlayer player = mc.player;
		BlockPos pos = player.blockPosition();

		String worldName = null;
		String worldFolder = null;
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			worldName = server.getWorldData().getLevelName();
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			if (root.getFileName() != null) {
				worldFolder = root.getFileName().toString();
			}
		} else if (mc.getCurrentServer() != null) {
			worldName = mc.getCurrentServer().name;
		}

		return new PhotoMeta(
				id,
				now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
				SharedConstants.getCurrentVersion().name(),
				KodlandCamera.MOD_ID + "@" + modVersion(),
				worldName,
				worldFolder,
				mc.level.dimension().identifier().toString(),
				mc.level.getBiome(pos).getRegisteredName(),
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				player.getYRot(),
				player.getXRot());
	}

	JsonObject toJson(int width, int height) {
		JsonObject world = new JsonObject();
		world.addProperty("name", worldName);
		world.addProperty("folder", worldFolder);

		JsonObject position = new JsonObject();
		position.addProperty("x", x);
		position.addProperty("y", y);
		position.addProperty("z", z);

		JsonObject rotation = new JsonObject();
		rotation.addProperty("yaw", round1(yaw));
		rotation.addProperty("pitch", round1(pitch));

		JsonObject json = new JsonObject();
		json.addProperty("version", CONTRACT_VERSION);
		json.addProperty("id", id);
		json.addProperty("takenAt", takenAt);
		json.addProperty("mc", mc);
		json.addProperty("mod", mod);
		json.add("world", world);
		json.addProperty("dimension", dimension);
		json.addProperty("biome", biome);
		json.add("pos", position);
		json.add("rotation", rotation);
		json.addProperty("width", width);
		json.addProperty("height", height);
		return json;
	}

	private static double round1(float value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(KodlandCamera.MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
