package net.scarabx.twoheavens.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.scarabx.twoheavens.combat.DrawnHandsGuard;
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

	/**
	 * Nothing moves in the inventory either, for the same reason nothing opens: no free
	 * hand.
	 *
	 * This is the ONE screen a drawn player can still reach, since E is a client key
	 * rather than a block, so it is the one place the stance rule has to be enforced
	 * from inside rather than at the door. It is state-based on purpose - the whole
	 * point is that a drawn player cannot rearrange anything, not that one item is
	 * special - which is what makes it the same rule as the locked hotbar rather than a
	 * second one.
	 *
	 * The version reverted earlier today was this same block WITHOUT the door being
	 * shut: chests opened and then ignored every click, so the player was handed a
	 * working-looking interface that did nothing. Now the door is shut and says why, and
	 * this says the same thing in the same words, so both read as one rule.
	 *
	 * sendAllDataToRemote because the client has already predicted the move; without the
	 * resync the two disagree about where the item is.
	 */
	@Inject(at = @At("HEAD"), method = "handleContainerClick", cancellable = true)
	private void twoheavens$blockInventoryMovesWhileDrawn(ServerboundContainerClickPacket packet, CallbackInfo info) {
		if (!this.player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			return;
		}
		this.player.containerMenu.sendAllDataToRemote();
		DrawnHandsGuard.tell(this.player, "inventory");
		info.cancel();
	}

	@Inject(at = @At("HEAD"), method = "handleSetCarriedItem", cancellable = true)
	private void twoheavens$blockSlotSwitchWhileDrawn(ServerboundSetCarriedItemPacket packet, CallbackInfo info) {
		if (this.player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			info.cancel();
		}
	}

}
