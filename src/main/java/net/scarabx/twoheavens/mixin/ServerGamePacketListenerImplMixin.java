package net.scarabx.twoheavens.mixin;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While swords are drawn (DrawnSwordsAttachment present), the player must not
 * be able to switch hotbar slots - they have to sheathe first. Without this,
 * scrolling/pressing a number key away from the fake katana/wakizashi slots
 * left those fake stacks sitting in arbitrary inventory slots, which is what
 * caused sheathe to lose track of them.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

	@Shadow
	public ServerPlayer player;

	@Inject(at = @At("HEAD"), method = "handleSetCarriedItem", cancellable = true)
	private void twoheavens$blockSlotSwitchWhileDrawn(ServerboundSetCarriedItemPacket packet, CallbackInfo info) {
		if (this.player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			info.cancel();
		}
	}
}
