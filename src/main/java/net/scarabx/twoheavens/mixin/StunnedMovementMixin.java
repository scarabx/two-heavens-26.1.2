package net.scarabx.twoheavens.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.scarabx.twoheavens.combat.StunAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a stunned entity still.
 *
 * Deliberately does NOT cancel travel: travel is what applies gravity and
 * friction, so cancelling it left a stunned mob hanging in the air. Zeroing the
 * horizontal delta first and letting travel run keeps the entity falling
 * normally while going nowhere sideways.
 *
 * Also does not touch hurtMarked. Setting it forced a position resync every
 * tick, which read as the entity being shoved.
 */
@Mixin(LivingEntity.class)
public abstract class StunnedMovementMixin {

	@Inject(method = "travel", at = @At("HEAD"))
	private void twoheavens$freezeWhileStunned(Vec3 travelVector, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!StunAttachment.isStunned(self)) {
			return;
		}
		Vec3 motion = self.getDeltaMovement();
		self.setDeltaMovement(0.0, motion.y, 0.0);
	}
}
