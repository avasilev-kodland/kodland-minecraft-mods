package com.kodland.camera.client;

import com.mojang.blaze3d.platform.NativeImage;

/** Pixel work on a grabbed frame. Every method returns a new image the caller owns. */
final class PhotoImages {
	/** Contract §3.1: the long side of a saved photo is at most this many pixels. */
	static final int MAX_SIDE = 1600;

	private PhotoImages() {
	}

	/** Centre crop to 4:3, then shrink so the long side fits {@link #MAX_SIDE}. Never upscales. */
	static NativeImage cropAndScale(NativeImage frame) {
		int w = frame.getWidth();
		int h = frame.getHeight();
		int cw = w;
		int ch = h;
		if (w * 3 > h * 4) {
			cw = h * 4 / 3;
		} else {
			ch = w * 3 / 4;
		}
		NativeImage out = fit(frame, (w - cw) / 2, (h - ch) / 2, cw, ch, MAX_SIDE);
		forceOpaque(out);
		return out;
	}

	/** A smaller copy for the on-screen preview texture. */
	static NativeImage scaled(NativeImage photo, int maxSide) {
		return fit(photo, 0, 0, photo.getWidth(), photo.getHeight(), maxSide);
	}

	private static NativeImage fit(NativeImage src, int x, int y, int w, int h, int maxSide) {
		double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
		int ow = Math.max(1, (int) Math.round(w * scale));
		int oh = Math.max(1, (int) Math.round(h * scale));
		NativeImage out = new NativeImage(ow, oh, false);
		src.resizeSubRectTo(x, y, w, h, out);
		return out;
	}

	/** The frame buffer's alpha is not meant for viewing; a saved photo must not be see-through. */
	private static void forceOpaque(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
			}
		}
	}
}
