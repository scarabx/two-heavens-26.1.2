package net.scarabx.twoheavens.combat;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Which step of the combat tutorial the player is on, if any.
 *
 * Nothing about drawing or the stab-then-finish combo is guessable: without R the
 * swords behave like ordinary items, and nothing suggests one blade sets up the
 * other. The recipes and tooltips say it, but only if you go looking, and the
 * moment you want to know is the moment you are holding both swords.
 *
 * Server-driven. Every transition is something the server already observes -
 * equipping the obi, drawing, a stun landing, a finisher connecting - so the
 * client is told which step it is on rather than trying to infer combat state it
 * only partly sees.
 *
 * Persistent AND copyOnDeath, like the other progress marks: this describes
 * something the player has LEARNED, and dying does not unlearn it.
 */
public final class CombatTutorialAttachment {

	/** Not started - no obi has been equipped yet. */
	public static final int NOT_STARTED = 0;
	/** Obi on: press R. */
	public static final int DRAW = 1;
	/** Drawn: left-click to stab and stun. */
	public static final int STUN = 2;
	/** A stun landed: right-click to finish. */
	public static final int FINISH = 3;
	/** Seen the whole thing - never shown again. */
	public static final int DONE = 4;

	public static final AttachmentType<Integer> TYPE = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT)
			.copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(TwoHeavens.id("combat_tutorial"));

	private CombatTutorialAttachment() {
	}

	public static void touch() {
	}

	public static int step(ServerPlayer player) {
		return player.getAttachedOrElse(TYPE, NOT_STARTED);
	}

	/**
	 * Moves to {@code next} only from {@code from}, so out-of-order events cannot skip
	 * a step or drag a finished tutorial back to the start - a player who has already
	 * seen it draws their swords every day afterwards.
	 */
	public static void advance(ServerPlayer player, int from, int next) {
		if (step(player) == from) {
			player.setAttached(TYPE, next);
		}
	}
}
