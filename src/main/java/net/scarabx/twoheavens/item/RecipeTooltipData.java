package net.scarabx.twoheavens.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The recipes an item is an ingredient IN - deliberately forward-looking, since
 * someone holding Tatara Clay has already made it and wants to know what it
 * becomes, not how it was produced.
 */
public record RecipeTooltipData(List<Entry> entries) implements TooltipComponent {

	/**
	 * @param grid     row-major, width*height long; empty stacks are blank cells.
	 *                 For a smelting entry only the first stack is used, as the input.
	 * @param width    grid columns, 1-3
	 * @param height   grid rows, 1-3
	 * @param result   what the recipe produces
	 * @param smelting draw as a furnace - input over fuel with a flame - rather than
	 *                 a crafting grid, so it does not imply the result is craftable
	 */
	public record Entry(List<ItemStack> grid, int width, int height, ItemStack result, boolean smelting) {
	}
}
