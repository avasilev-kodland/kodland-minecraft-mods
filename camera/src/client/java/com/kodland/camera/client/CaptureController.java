package com.kodland.camera.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.kodland.camera.KodlandCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One shot, step by step: hide the HUD (and with it the hand and the crosshair),
 * let a couple of frames render without it, read the frame back, put the HUD back,
 * show the polaroid. Everything runs on the client thread through the tick event;
 * the GPU read-back hands its result over through {@link #ready}.
 */
final class CaptureController {
	private static final Logger LOG = LoggerFactory.getLogger(KodlandCamera.MOD_ID);

	/** Ticks with the HUD hidden before the frame is read; at 20 TPS a frame always fits. */
	private static final int HIDDEN_TICKS = 2;
	/** If the read-back never answers, give the HUD back instead of hiding it forever. */
	private static final int TIMEOUT_TICKS = 40;
	private static final long COOLDOWN_MS = 1000;

	private enum State { IDLE, HIDING, GRABBING }

	private State state = State.IDLE;
	private int ticks;
	private boolean weHidHud;
	private long lastShotAt;
	private PhotoMeta meta;
	private volatile NativeImage ready;

	/** The camera was used. Ignored while a shot is in flight or during the cooldown. */
	void request() {
		Minecraft mc = Minecraft.getInstance();
		if (state != State.IDLE || mc.player == null || mc.level == null || mc.gui.screen() != null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastShotAt < COOLDOWN_MS) {
			return;
		}
		lastShotAt = now;
		meta = PhotoMeta.capture(mc);
		weHidHud = !mc.gui.hud.isHidden();
		if (weHidHud) {
			mc.gui.hud.toggle();
		}
		state = State.HIDING;
		ticks = 0;
	}

	void tick(Minecraft mc) {
		switch (state) {
			case IDLE -> { }
			case HIDING -> {
				if (mc.level == null) {
					reset(mc);
				} else if (++ticks >= HIDDEN_TICKS) {
					state = State.GRABBING;
					ticks = 0;
					grab(mc);
				}
			}
			case GRABBING -> {
				NativeImage photo = ready;
				if (photo != null) {
					ready = null;
					PhotoMeta shot = meta;
					reset(mc);
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f));
					if (mc.level != null) {
						mc.gui.setScreen(new PolaroidScreen(photo, shot));
					} else {
						photo.close();
					}
				} else if (++ticks >= TIMEOUT_TICKS || mc.level == null) {
					LOG.warn("Photo read-back did not answer in {} ticks, shot dropped", TIMEOUT_TICKS);
					reset(mc);
				}
			}
		}
	}

	private void grab(Minecraft mc) {
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), frame -> {
			try {
				NativeImage photo = PhotoImages.cropAndScale(frame);
				if (state == State.GRABBING) {
					ready = photo;
				} else {
					photo.close(); // timed out meanwhile
				}
			} catch (RuntimeException e) {
				LOG.error("Could not prepare the photo", e);
			} finally {
				frame.close();
			}
		});
	}

	private void reset(Minecraft mc) {
		if (weHidHud && mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
		}
		weHidHud = false;
		state = State.IDLE;
		meta = null;
		ticks = 0;
	}
}
