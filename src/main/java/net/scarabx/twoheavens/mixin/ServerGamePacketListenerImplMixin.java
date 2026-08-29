package net.scarabx.twoheavens.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
 * While swords are drawn (DrawnSwordsAttachment present) the fake katana and
 * wakizashi must not be able to LEAVE the player's hands - not to another slot,
 * not into a container, not onto the ground, and above all not into a crafting
 * grid.
 *
 * The fakes are ordinary ModItems.KATANA and ModItems.WAKIZASHI stacks carrying
 * only a CustomData marker, so every recipe accepts them. That made an item dupe:
 * draw, craft the two fakes into a Daisho Saya, sheathe. Sheathe strips fakes from
 * the inventory and finds none (they were consumed), then restores the real stored
 * swords - so each cycle produced a free saya, and a saya disassembles into a real
 * katana and wakizashi. Dropping a fake with Q was the same bug by another route:
 * the sweep only covers the inventory, so the item entity outlived the sheathe.
 *
 * All three handlers are refused WHOLESALE while drawn rather than inspecting each
 * click for a fake stack. A container click has many modes - quick-move, hotbar
 * swap, throw, drag - each reaching different slots, so a per-mode check is a list
 * that has to stay exhaustive against a vanilla class that is free to grow. Given
 * what the gap costs, the rule that cannot have a hole in it is worth more than the
 * convenience: sheathe first, then rearrange. That is the same rule the hotbar
 * switch below already applies, extended to the places a slot can be reached from.
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

	/**
	 * The dupe itself. Cancelling at HEAD means the menu never runs the click, so no
	 * recipe ever sees a fake sword as an ingredient.
	 *
	 * sendAllDataToRemote is not optional. The client has already predicted the click
	 * locally, so without a resync it would show the stack where it moved it to and
	 * the server would disagree - a desync that looks exactly like a second dupe.
	 * Vanilla does the same thing in its own spectator branch of this method.
	 *
	 * The screen still OPENS while drawn, and everything in it is still readable. Only
	 * moving items is refused.
	 */
	@Inject(at = @At("HEAD"), method = "handleContainerClick", cancellable = true)
	private void twoheavens$blockContainerClickWhileDrawn(ServerboundContainerClickPacket packet, CallbackInfo info) {
		if (this.player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			this.player.containerMenu.sendAllDataToRemote();
			info.cancel();
		}
	}

	/**
	 * Q and F, the two routes out of the hands that never touch a container menu.
	 *
	 * Dropping is the one that duped: a fake sword thrown on the ground is a real
	 * katana item entity, and sheathe only sweeps the INVENTORY, so it survived to be
	 * picked back up. The offhand swap is guarded because the draw is staggered by
	 * several ticks - for that window one hand holds a real item and the other a fake,
	 * and swapping them puts the two swaps back out of step with the slots sheathe is
	 * going to restore to.
	 *
	 * Only these three actions. The rest of the packet is left alone: RELEASE_USE_ITEM
	 * and the destroy actions must keep flowing, and a drawn player is already stopped
	 * from mining by SwordBlockGuard, which refuses a left-click at a block whenever a
	 * blade is in the main hand. Blocking them here as well would be a second rule
	 * saying the same thing in a place nobody would look for it.
	 */
	@Inject(at = @At("HEAD"), method = "handlePlayerAction", cancellable = true)
	private void twoheavens$blockDropWhileDrawn(ServerboundPlayerActionPacket packet, CallbackInfo info) {
		if (!this.player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			return;
		}
		ServerboundPlayerActionPacket.Action action = packet.getAction();
		if (action == ServerboundPlayerActionPacket.Action.DROP_ITEM
				|| action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
				|| action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			info.cancel();
		}
	}
}
