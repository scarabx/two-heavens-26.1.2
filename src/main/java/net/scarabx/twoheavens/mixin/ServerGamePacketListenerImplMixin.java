package net.scarabx.twoheavens.mixin;

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
 * While swords are drawn the player cannot switch hotbar slots, drop, or swap
 * hands. All three keep the fake katana and wakizashi in the hands they were put
 * in, which is what sheathe assumes when it restores the real items.
 *
 * NOT a dupe guard. Crafting the two fakes into a Daisho Saya and sheathing does
 * duplicate them, and that is deliberately LEFT ALONE: you need both finished
 * swords to do it, so the reward is a second pair for a player who has two arms
 * and has already completed the entire smithing chain. The fix cost more than the
 * bug - blocking container clicks while drawn made a chest open and then silently
 * refuse every click, which reads as a broken mod rather than as a rule. See
 * notes.md.
 *
 * What these guards prevent is ITEM LOSS, which is a different problem:
 *
 *   hotbar switch  scrolling away left fake stacks stranded in arbitrary slots,
 *                  which is how sheathe lost track of them
 *   Q / drop-all   a dropped fake is a real-looking katana on the ground, and the
 *                  next sheathe SILENTLY DELETES it - stripFakeSwords sweeps the
 *                  whole inventory - so a player who picks their own drop back up
 *                  loses a sword they had no reason to think was phantom
 *   F offhand swap the draw is staggered by several ticks, so for that window one
 *                  hand is real and the other fake, and swapping puts them out of
 *                  step with the slots restoreMainHand will write to
 *
 * All three cost the player nothing real: while drawn both hands hold fakes, so
 * there is nothing legitimate to drop or swap, and the hotbar is locked anyway.
 * That is the line - these are invisible in normal play, which the container block
 * was not.
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
	 * Q and F, the two routes out of the hands that never touch a container menu.
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
