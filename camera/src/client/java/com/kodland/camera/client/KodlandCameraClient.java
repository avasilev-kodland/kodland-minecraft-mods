package com.kodland.camera.client;

import com.kodland.camera.CameraItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class KodlandCameraClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CaptureController controller = new CaptureController();
		CameraItem.onClientUse = controller::request;
		ClientTickEvents.END_CLIENT_TICK.register(controller::tick);
	}
}
