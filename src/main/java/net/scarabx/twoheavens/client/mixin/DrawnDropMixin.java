package net.scarabx.twoheavens.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client half of the drop guard.
 *
 * `LocalPlayer#drop` removes the stack from the selected slot BEFORE sending the
 * packet - that removal is a prediction, not a request. So blocking the drop on the
 * server alone left the sword gone from hand and inventory on screen, with nothing
 * to put it back until the next container sync. Exactly the shape of the earlier
 * bug where opening a chest made a sword vanish: a client that predicts an outcome
 * the server refuses.
 *
 * **Any server-side refusal of a player action needs its client half**, or the
 * refusal looks like item loss. That is the third time today.
 */
@Mixin(LocalPlayer.class)
public class DrawnDropMixin {

	@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
	private void twoheavens$blockDropWhileDrawn(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (((LocalPlayer) (Object) this).hasAttached(DrawnSwordsAttachment.TYPE)) {
			cir.setReturnValue(false);
		}
	}
}
