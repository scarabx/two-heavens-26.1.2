package net.scarabx.twoheavens.mixin;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.scarabx.twoheavens.combat.FakeDrawnSword;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A drawn sword is not an ingredient.
 *
 * The swords worn on the obi are ordinary KATANA and WAKIZASHI stacks carrying a
 * CustomData marker, and until now NOTHING read that marker outside sheathe. So
 * every recipe accepted them, and crafting the pair into a Daisho Saya and then
 * sheathing left the saya behind while the real swords came back.
 *
 * This is the root fix, and it replaces an earlier one that was worse than the bug:
 * blocking container clicks while drawn stopped the dupe but made a chest open and
 * then silently refuse every click, which reads as a broken mod rather than a rule.
 * Marking the stack unusable costs nothing anyone can see - a phantom sword was
 * never a legitimate ingredient - and it holds for every recipe at once, including
 * any added later, instead of guarding the routes to the grid one at a time.
 *
 * `slotChangedCraftingGrid` is the whole surface: it is the one static helper both
 * the 2x2 in the inventory (InventoryMenu) and the 3x3 table (CraftingMenu) call to
 * recompute their result. Server-side, so the client cannot be talked out of it.
 *
 * The empty result is written exactly the way vanilla writes its own empty case -
 * result slot, remote slot, and the packet - because cancelling without that would
 * leave whatever result was showing before the fake was added.
 */
@Mixin(CraftingMenu.class)
public class FakeSwordCraftingMixin {

	@Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true)
	private static void twoheavens$refuseFakeSwords(AbstractContainerMenu menu, ServerLevel level, Player player,
													CraftingContainer container, ResultContainer resultSlots,
													@Nullable RecipeHolder<CraftingRecipe> recipeHint,
													CallbackInfo ci) {
		boolean holdsFake = false;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (FakeDrawnSword.isFake(container.getItem(i))) {
				holdsFake = true;
				break;
			}
		}
		if (!holdsFake) {
			return;
		}

		resultSlots.setItem(0, ItemStack.EMPTY);
		menu.setRemoteSlot(0, ItemStack.EMPTY);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
					menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
		}
		ci.cancel();
	}
}
