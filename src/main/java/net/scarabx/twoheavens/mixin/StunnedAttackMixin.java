package net.scarabx.twoheavens.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.scarabx.twoheavens.combat.StunAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a stunned mob landing a hit. Weakness only reduced the damage to zero -
 * the mob still swung, which did not read as stunned.
 */
@Mixin(Mob.class)
public abstract class StunnedAttackMixin {

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void twoheavens$blockAttackWhileStunned(ServerLevel level, Entity target,
													CallbackInfoReturnable<Boolean> cir) {
		if (StunAttachment.isStunned((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
