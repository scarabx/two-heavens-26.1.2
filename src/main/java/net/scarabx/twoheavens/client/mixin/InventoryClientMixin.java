package net.scarabx.twoheavens.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side (ServerGamePacketListenerImplMixin) rejecting the slot-change
 * packet stops the swap from actually taking effect, but the client picks
 * its own hotbar slot locally BEFORE that packet even reaches the server -
 * so without this, the hotbar selector still visibly moves and the swords
 * still appear to leave your hands even though the server silently kept the
 * old slot selected. Blocking it here too keeps client and server in sync
 * instead of just fighting over who's right.
 */
@Mixin(Inventory.class)
public class InventoryClientMixin {

	@Inject(at = @At("HEAD"), method = "setSelectedSlot", cancellable = true)
	private void twoheavens$blockSlotSwitchWhileDrawn(int selected, CallbackInfo info) {
		Inventory self = (Inventory) (Object) this;
		Player owner = self.player;
		if (owner != null && owner == Minecraft.getInstance().player
				&& owner.hasAttached(DrawnSwordsAttachment.TYPE)) {
			info.cancel();
		}
	}
}
