package net.scarabx.twoheavens.combat;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.LivingEntity;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * A wakizashi stun, held as a game-time expiry on the target rather than as a
 * mob effect.
 *
 * Effects were the obvious route but wrong here: the swirl and HUD icon made a
 * sword strike read as a thrown potion, milk removed it, and Weakness only zeroes
 * the damage - the mob still swings.
 *
 * Nothing ticks this down. Every check compares against the current game time, so
 * an expired stun simply stops matching and the attachment is overwritten by the
 * next one.
 */
public final class StunAttachment {

	public static final AttachmentType<Long> TYPE = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG)
			.buildAndRegister(TwoHeavens.id("stunned_until"));

	private StunAttachment() {
	}

	// Loads the class during mod init so TYPE actually registers, rather than
	// lazily on first use - same reasoning as DrawnSwordsAttachment.touch().
	public static void touch() {
	}

	public static void stun(LivingEntity target, int durationTicks) {
		target.setAttached(TYPE, target.level().getGameTime() + durationTicks);
	}

	public static boolean isStunned(LivingEntity entity) {
		Long until = entity.getAttached(TYPE);
		return until != null && entity.level().getGameTime() < until;
	}

	public static void clear(LivingEntity entity) {
		entity.removeAttached(TYPE);
	}
}
