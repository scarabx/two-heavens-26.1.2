package net.scarabx.twoheavens.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.scarabx.twoheavens.combat.StunAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a stunned entity still. Only the horizontal delta is zeroed, so gravity
 * still applies - a mob stunned mid-air falls instead of hanging there.
 */
@Mixin(LivingEntity.class)
public abstract class StunnedMovementMixin {

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void twoheavens$freezeWhileStunned(Vec3 travelVector, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!StunAttachment.isStunned(self)) {
			return;
		}
		Vec3 motion = self.getDeltaMovement();
		self.setDeltaMovement(0.0, motion.y, 0.0);
		self.hurtMarked = true;
		ci.cancel();
	}
}
