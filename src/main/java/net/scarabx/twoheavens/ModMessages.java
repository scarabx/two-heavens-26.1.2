package net.scarabx.twoheavens;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * One way out for every chat message this mod sends, so they can be recognised again
 * on the client.
 *
 * Chat is not drawn behind an open screen, and several of these fire from exactly
 * there - the obi handoff lands as the player takes their first sword out of a
 * crafting table. The client repeats them as a toast in that case, which means it has
 * to tell OUR lines from the server's and other mods'.
 *
 * The marker is a Style INSERTION, which carries an arbitrary string, is invisible,
 * and survives the trip to the client. Matching on the translation key was the first
 * attempt and does not work: most of these are `Component.literal`, so the most
 * important message of the lot - the handoff - would have been missed.
 *
 * Insertion is normally the shift-click-to-type text. Nothing here is shift-clickable
 * in practice, so borrowing it costs nothing.
 */
public final class ModMessages {

	public static final String MARKER = "twoheavens";

	private ModMessages() {
	}

	/** Marks a component as ours, without changing how it looks. */
	public static MutableComponent mark(MutableComponent component) {
		return component.withStyle(style -> style.withInsertion(MARKER));
	}

	public static void send(ServerPlayer player, MutableComponent message) {
		player.sendSystemMessage(mark(message));
	}

	public static boolean isOurs(Component component) {
		return MARKER.equals(component.getStyle().getInsertion());
	}
}
