package net.scarabx.twoheavens.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.scarabx.twoheavens.combat.StunAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a stunned entity still, and gives its speed back when the stun expires.
 *
 * This used to zero the entity's horizontal delta movement at the head of
 * LivingEntity#travel, which did nothing at all: travel hands the AI's input
 * vector to handleRelativeFrictionAndCalculateMovement and then calls
 * setDeltaMovement with the result, so anything written beforehand is discarded
 * within the same call. StunAttachment now applies a -100% MOVEMENT_SPEED
 * modifier instead, which is the value that calculation actually reads.
 *
 * Cancelling travel outright is still not an option - travel is what applies
 * gravity, so cancelling it leaves a stunned mob hanging in the air.
 *
 * Nothing counts the stun down (the attachment holds a game-time expiry), so
 * this per-tick sync is what notices expiry and removes the modifier.
 */
@Mixin(LivingEntity.class)
public abstract class StunnedMovementMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void twoheavens$syncStun(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		StunAttachment.sync(self);
	}
}
