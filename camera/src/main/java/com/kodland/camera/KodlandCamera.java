package com.kodland.camera;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class KodlandCamera implements ModInitializer {
	public static final String MOD_ID = "kodland_camera";

	public static final ResourceKey<Item> CAMERA_KEY =
			ResourceKey.create(Registries.ITEM, id("camera"));

	public static Item CAMERA;

	@Override
	public void onInitialize() {
		CAMERA = Registry.register(BuiltInRegistries.ITEM, CAMERA_KEY,
				new CameraItem(new Item.Properties().setId(CAMERA_KEY).stacksTo(1)));

		// No recipe on purpose: the camera is found through the creative search.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(CAMERA));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
