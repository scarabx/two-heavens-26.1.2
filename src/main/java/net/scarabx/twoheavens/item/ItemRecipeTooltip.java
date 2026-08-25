package net.scarabx.twoheavens.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The two halves of the "what does this make?" tooltip, shared by every item
 * class that wants it.
 *
 * Mod items call these from their own appendHoverText/getTooltipImage overrides.
 * Only vanilla items - whose classes we cannot touch - go through
 * ItemTooltipMixin instead.
 */
public final class ItemRecipeTooltip {

	private ItemRecipeTooltip() {
	}

	/** The dim prompt, shown only while Shift is up and only if there is a recipe to show. */
	public static void appendPrompt(ItemStack stack, Consumer<Component> consumer) {
		if (ModRecipeTooltips.forIngredient(stack.getItem()) == null || ShiftState.isDown()) {
			return;
		}
		consumer.accept(Component.translatable("tooltip.twoheavens.shift_for_recipe"));
	}

	/** The grid itself, shown while Shift is held. */
	public static Optional<TooltipComponent> image(ItemStack stack) {
		if (!ShiftState.isDown()) {
			return Optional.empty();
		}
		RecipeTooltipData data = ModRecipeTooltips.forIngredient(stack.getItem());
		return data == null ? Optional.empty() : Optional.of(data);
	}
}
