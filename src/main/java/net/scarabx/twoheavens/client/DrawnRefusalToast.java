package net.scarabx.twoheavens.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Shows one of this mod's chat messages where a screen is open, because chat is not drawn
 * behind one.
 *
 * The refusal reaches the player as a chat line everywhere else, which is right: at a
 * chest, a frame or a Q press there is no screen in the way and chat is visible. The
 * inventory is the exception and the only one - it is the sole screen a drawn player
 * can still open, and it is exactly where the message was invisible. They would hear
 * the ping, see nothing move, and click again.
 *
 * A toast rather than anything custom: it is the vanilla mechanism for a notification
 * that must survive an open screen (advancement popups use it), so it needs no
 * rendering code and looks like part of the game rather than part of this mod.
 */
public final class DrawnRefusalToast {

	private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId();

	private DrawnRefusalToast() {
	}

	/** True when a screen is covering the chat overlay. */
	public static boolean screenIsOpen() {
		return Minecraft.getInstance().screen != null;
	}

	public static void show(Component message) {
		// addOrUpdate, not add: a repeated message refreshes the one toast instead of
		// stacking a column of identical ones down the corner.
		SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), ID, message, null);
	}
}
