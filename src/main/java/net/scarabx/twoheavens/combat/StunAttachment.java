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

	/**
	 * The wakizashi's no-obi cut applies a brief partial slow rather than a full
	 * stun - same machinery, smaller amplitude, its own expiry so the two never
	 * overwrite each other. A stunned target that is also slowed simply keeps both
	 * modifiers; -100% already wins.
	 */
	public static final AttachmentType<Long> SLOW_TYPE = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG)
			.buildAndRegister(TwoHeavens.id("slowed_until"));

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

	private static final Identifier CUT_SLOW_ID = TwoHeavens.id("cut_slowed");
	private static final AttributeModifier CUT_SLOW = new AttributeModifier(
			CUT_SLOW_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

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
		Long stunnedUntil = entity.getAttached(TYPE);
		Long slowedUntil = entity.getAttached(SLOW_TYPE);
		// The overwhelmingly common case: an entity that has never been hit by a
		// wakizashi. This runs for every living entity every tick, so it has to cost
		// two null map lookups and nothing else - no attribute access, no modifier
		// removal, no game-time read.
		if (stunnedUntil == null && slowedUntil == null) {
			return;
		}

		// From here the attachment is the single source of truth and the modifier is
		// re-derived from it every tick, rather than being applied once and trusted.
		//
		// The attachment is deliberately NOT dropped on expiry. Dropping it sends the
		// entity back into the cheap path above, after which nothing ever inspects it
		// again - so a modifier that outlived its attachment for any reason (a missed
		// removal, death, an NBT round-trip) would be permanent, with no state left
		// saying why the mob is slow. Keeping the attachment costs one long and makes
		// every tick self-correcting.
		long now = entity.level().getGameTime();
		if (stunnedUntil != null) {
			if (now < stunnedUntil) {
				apply(entity, SLOW_ID, SLOW);
			} else {
				remove(entity, SLOW_ID);
			}
		}
		if (slowedUntil != null) {
			if (now < slowedUntil) {
				apply(entity, CUT_SLOW_ID, CUT_SLOW);
			} else {
				remove(entity, CUT_SLOW_ID);
			}
		}
	}

	public static void slow(LivingEntity target, int durationTicks) {
		target.setAttached(SLOW_TYPE, target.level().getGameTime() + durationTicks);
		apply(target, CUT_SLOW_ID, CUT_SLOW);
	}

	public static boolean isSlowed(LivingEntity entity) {
		Long until = entity.getAttached(SLOW_TYPE);
		return until != null && entity.level().getGameTime() < until;
	}

	private static void apply(LivingEntity entity, Identifier id, AttributeModifier modifier) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(id)) {
			speed.addTransientModifier(modifier);
		}
	}

	private static void remove(LivingEntity entity, Identifier id) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(id);
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
		// Expire it rather than deleting it, so sync keeps watching this entity and
		// can strip the modifier again if it ever reappears.
		entity.setAttached(TYPE, entity.level().getGameTime());
		removeModifier(entity);
	}
}
