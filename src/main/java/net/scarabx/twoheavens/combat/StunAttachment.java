package net.scarabx.twoheavens.combat;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

	// -100% movement speed. Freezing the entity by zeroing its delta movement does
	// not work: LivingEntity#travel recomputes the delta from the AI's input vector
	// inside the same call, discarding anything set beforehand. The speed attribute
	// is what that calculation reads, so this is the point that actually holds.
	private static final Identifier SLOW_ID = TwoHeavens.id("stunned");
	private static final AttributeModifier SLOW = new AttributeModifier(
			SLOW_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	public static void stun(LivingEntity target, int durationTicks) {
		target.setAttached(TYPE, target.level().getGameTime() + durationTicks);
		applyModifier(target);
	}

	/**
	 * Keeps the speed modifier in step with the expiry. Called every tick from
	 * StunnedMovementMixin: nothing counts the stun down, so this is what notices
	 * that it has run out and gives the entity its speed back.
	 */
	public static void sync(LivingEntity entity) {
		if (isStunned(entity)) {
			applyModifier(entity);
		} else {
			removeModifier(entity);
		}
	}

	private static void applyModifier(LivingEntity entity) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(SLOW_ID)) {
			speed.addTransientModifier(SLOW);
		}
	}

	private static void removeModifier(LivingEntity entity) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW_ID);
		}
	}

	public static boolean isStunned(LivingEntity entity) {
		Long until = entity.getAttached(TYPE);
		return until != null && entity.level().getGameTime() < until;
	}

	public static void clear(LivingEntity entity) {
		entity.removeAttached(TYPE);
		removeModifier(entity);
	}
}
