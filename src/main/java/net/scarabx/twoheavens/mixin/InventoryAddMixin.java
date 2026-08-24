package net.scarabx.twoheavens.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.event.TutorialProgressAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the starter-hint counters up to date the moment items arrive, instead of
 * polling every inventory on a timer.
 *
 * Every acquisition path - ground pickup, crafting output, containers, commands -
 * funnels through Inventory#add, so one hook covers all of them. The counters are
 * max() based, so firing more often than strictly needed costs nothing and is
 * always safe.
 */
@Mixin(Inventory.class)
public class InventoryAddMixin {

	@Shadow
	public net.minecraft.world.entity.player.Player player;

	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
	private void twoheavens$trackStarterProgress(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (this.player instanceof ServerPlayer serverPlayer) {
			TutorialProgressAttachment.sample(serverPlayer);
		}
	}
}
