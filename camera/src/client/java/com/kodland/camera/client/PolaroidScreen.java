package com.kodland.camera.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.kodland.camera.KodlandCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The fresh photo as a polaroid card, slightly tilted, with two icon buttons under it:
 * delete and save. No visible text on purpose - the names exist only for the narrator.
 * Esc counts as delete: nothing is written unless the child presses save.
 */
public final class PolaroidScreen extends Screen {
	private static final Identifier DELETE_SPRITE = KodlandCamera.id("polaroid/delete");
	private static final Identifier SAVE_SPRITE = KodlandCamera.id("polaroid/save");

	private static final int PREVIEW_MAX_SIDE = 640;
	private static final int PAD = 8;
	private static final int PAD_BOTTOM = 30;
	private static final int BUTTON = 24;
	private static final int ICON = 16;
	private static final int GAP = 10;
	private static final float TILT_DEGREES = -2.5f;
	private static final long FLASH_MS = 220;
	private static final int PAPER = 0xFFF8F5EE;
	private static final int SHADOW = 0x70000000;

	private static int textureCounter;

	private final PhotoMeta meta;
	private final Identifier previewId;
	private final int previewW;
	private final int previewH;
	private final long openedAt = Util.getMillis();
	/** Owned by the screen until it is handed to the writer or closed. */
	private NativeImage photo;

	private int photoW;
	private int photoH;
	private int cardCenterX;
	private int cardCenterY;

	PolaroidScreen(NativeImage photo, PhotoMeta meta) {
		super(Component.translatable("kodland_camera.polaroid.title"));
		this.photo = photo;
		this.meta = meta;
		NativeImage preview = PhotoImages.scaled(photo, PREVIEW_MAX_SIDE);
		this.previewW = preview.getWidth();
		this.previewH = preview.getHeight();
		this.previewId = KodlandCamera.id("polaroid/preview_" + textureCounter++);
		Minecraft.getInstance().getTextureManager()
				.register(previewId, new DynamicTexture(() -> "Kodland Camera polaroid", preview));
	}

	@Override
	protected void init() {
		int maxPhotoH = height - 2 * GAP - PAD - PAD_BOTTOM - GAP - BUTTON;
		photoH = Math.max(48, Math.min(240, maxPhotoH));
		photoW = photoH * previewW / previewH;
		if (photoW > width - 2 * GAP - 2 * PAD) {
			photoW = width - 2 * GAP - 2 * PAD;
			photoH = photoW * previewH / previewW;
		}
		int cardH = PAD + photoH + PAD_BOTTOM;
		int top = (height - (cardH + GAP + BUTTON)) / 2;
		cardCenterX = width / 2;
		cardCenterY = top + cardH / 2;

		int buttonsY = top + cardH + GAP;
		SpriteIconButton delete = SpriteIconButton
				.builder(Component.translatable("kodland_camera.polaroid.delete"), button -> discard(), true)
				.size(BUTTON, BUTTON)
				.sprite(DELETE_SPRITE, ICON, ICON)
				.build();
		delete.setX(width / 2 - GAP / 2 - BUTTON);
		delete.setY(buttonsY);
		addRenderableWidget(delete);

		SpriteIconButton save = SpriteIconButton
				.builder(Component.translatable("kodland_camera.polaroid.save"), button -> save(), true)
				.size(BUTTON, BUTTON)
				.sprite(SAVE_SPRITE, ICON, ICON)
				.build();
		save.setX(width / 2 + GAP / 2);
		save.setY(buttonsY);
		addRenderableWidget(save);
		setInitialFocus(save);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		int cardW = PAD + photoW + PAD;
		int cardH = PAD + photoH + PAD_BOTTOM;
		int left = -cardW / 2;
		int top = -cardH / 2;

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cardCenterX, cardCenterY);
		pose.rotate((float) Math.toRadians(TILT_DEGREES));
		graphics.fill(left + 3, top + 4, left + cardW + 3, top + cardH + 4, SHADOW);
		graphics.fill(left, top, left + cardW, top + cardH, PAPER);
		graphics.blit(RenderPipelines.GUI_TEXTURED, previewId, left + PAD, top + PAD, 0.0f, 0.0f,
				photoW, photoH, previewW, previewH, previewW, previewH);
		pose.popMatrix();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		long since = Util.getMillis() - openedAt;
		if (since < FLASH_MS) {
			int alpha = (int) (0xE0 * (1.0 - (double) since / FLASH_MS));
			graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				save();
				return true;
			}
			case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
				discard();
				return true;
			}
			default -> {
				return super.keyPressed(event);
			}
		}
	}

	private void save() {
		NativeImage toSave = photo;
		if (toSave == null) {
			return;
		}
		photo = null;
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
		PhotoWriter.save(toSave, meta).whenComplete((path, error) -> mc.execute(() -> mc.gui.hud.setOverlayMessage(
				Component.translatable(error == null ? "kodland_camera.photo_saved" : "kodland_camera.photo_failed"),
				false)));
		onClose();
	}

	private void discard() {
		onClose();
	}

	@Override
	public void removed() {
		if (photo != null) {
			photo.close();
			photo = null;
		}
		Minecraft.getInstance().getTextureManager().release(previewId);
		super.removed();
	}
}
