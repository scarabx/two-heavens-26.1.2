package net.scarabx.twoheavens.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
 * refusal looks like item loss - but the client half must suppress the PREDICTION
 * only. Cancelling the whole method also cancelled the send, so the server never
 * saw the keypress and the drop was refused in silence.
 */
@Mixin(LocalPlayer.class)
public class DrawnDropMixin {

	@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
	private void twoheavens$blockDropWhileDrawn(boolean all, CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!self.hasAttached(DrawnSwordsAttachment.TYPE)) {
			return;
		}
		// Suppress the PREDICTION, not the packet. Cancelling outright stopped the send
		// as well, so the server never saw the keypress and never sent its refusal - the
		// drop was blocked silently, which is the same failure the chest guard had an
		// hour earlier. Sending it by hand skips only removeFromSelected, so the server
		// refuses and speaks while nothing disappears on screen.
		self.connection.send(new ServerboundPlayerActionPacket(
				all ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
						: ServerboundPlayerActionPacket.Action.DROP_ITEM,
				BlockPos.ZERO, Direction.DOWN));
		cir.setReturnValue(false);
	}
}
