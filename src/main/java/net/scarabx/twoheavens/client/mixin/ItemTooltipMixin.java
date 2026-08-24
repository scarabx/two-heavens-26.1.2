package net.scarabx.twoheavens.client.mixin;

import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.item.ModRecipeTooltips;
import net.scarabx.twoheavens.item.RecipeTooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shows what a crafting ingredient turns into, without needing a recipe-viewer
 * mod installed. Unshifted it is a single dim prompt; holding Shift swaps in the
 * grid itself, drawn by ClientRecipeTooltip.
 *
 * Client-side only - twoheavens$shiftDown() must never load on a dedicated server.
 */
@Mixin(Item.class)
public class ItemTooltipMixin {

	/** Screen.hasShiftDown() no longer exists in this version - poll the window directly. */
	private static boolean twoheavens$shiftDown() {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT)
				|| InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_RSHIFT);
	}

	@Inject(method = "appendHoverText", at = @At("TAIL"))
	private void twoheavens$recipePrompt(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
										  Consumer<Component> consumer, TooltipFlag flag, CallbackInfo ci) {
		if (ModRecipeTooltips.forIngredient(stack.getItem()) == null || twoheavens$shiftDown()) {
			return;
		}
		consumer.accept(Component.translatable("tooltip.twoheavens.shift_for_recipe")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void twoheavens$recipeGrid(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!twoheavens$shiftDown()) {
			return;
		}
		RecipeTooltipData data = ModRecipeTooltips.forIngredient(stack.getItem());
		if (data != null) {
			cir.setReturnValue(Optional.of(data));
		}
	}
}
