package net.scarabx.twoheavens.item;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Whether Shift is held, asked from common item code.
 *
 * Tooltips are only ever built client-side, but these item classes also load on a
 * dedicated server - so the key polling sits in a client-only nested class that is
 * never reached there.
 */
public final class ShiftState {

	private static final boolean CLIENT =
			FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

	private ShiftState() {
	}

	public static boolean isDown() {
		return CLIENT && ClientShift.isDown();
	}

	@Environment(EnvType.CLIENT)
	private static final class ClientShift {
		static boolean isDown() {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.getWindow() == null) {
				return false;
			}
			return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT)
					|| InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RSHIFT);
		}
	}
}
