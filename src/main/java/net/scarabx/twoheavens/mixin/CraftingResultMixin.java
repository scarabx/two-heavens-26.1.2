package net.scarabx.twoheavens.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.event.TutorialProgressAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of InventoryAddMixin's job.
 *
 * That mixin covers everything arriving through Inventory#add - ground pickups
 * above all - but a crafting result never goes near it: taking one moves the
 * stack through the menu (moveItemStackTo, or straight onto the cursor), so
 * nothing in the inventory's own add path ever runs.
 *
 * Satetsu is unaffected, being mined. Tatara Clay is not: it is CRAFTED, and it
 * is the goal the second starter hint counts down, so without this the hint
 * could sit there asking for clay the player had already made - until their next
 * ground pickup or next login happened to resample them.
 *
 * onTake is the single chokepoint for every crafting output regardless of how it
 * was clicked, and sample() is a max() over the whole inventory, so firing here
 * as well as there is safe rather than double-counting.
 */
@Mixin(ResultSlot.class)
public class CraftingResultMixin {

	@Inject(method = "onTake", at = @At("TAIL"))
	private void twoheavens$trackCraftedProgress(Player player, ItemStack carried, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			TutorialProgressAttachment.sample(serverPlayer);
		}
	}
}
