package com.kodland.camera;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The camera itself does nothing on the server: a photo is a picture of what this
 * player sees, so the whole capture lives on the client. The common source set
 * cannot see client classes, so the client entrypoint plugs itself in here.
 */
public final class CameraItem extends Item {
	/** Set by the client entrypoint; a no-op on a dedicated server. */
	public static volatile Runnable onClientUse = () -> {};

	public CameraItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			onClientUse.run();
		}
		return InteractionResult.SUCCESS;
	}
}
