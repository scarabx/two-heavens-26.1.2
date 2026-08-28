package net.scarabx.twoheavens.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.item.ModRecipeTooltips;
import net.scarabx.twoheavens.item.RecipeTooltipData;
import net.scarabx.twoheavens.item.ShiftState;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The hot kera. Its own class purely so it can carry a tooltip: it used to be a
 * plain Item::new, which meant no instruction and not even the recipe prompt,
 * despite having an entry in ModRecipeTooltips.
 *
 * This is the step where guidance ran out - the player is holding something that
 * is cooling in their hands with no indication that an anvil is where it goes.
 *
 * Its Shift tooltip deliberately shows the SMITHING ANVIL's recipe rather than its
 * own. The generic lookup would offer the kera's own smelting entry - the furnace
 * turning a Kera into this - which is how the thing already in your hand came to
 * be, and useless. "Place on a Smithing Anvil" is a dead end for anyone who does
 * not know what one is or how to get one, and this is the only surface that can
 * answer that: the anvil is not in your inventory to be hovered, and the HUD
 * cannot fire until one exists in the world.
 */
public class MoltenKeraItem extends Item {

	public MoltenKeraItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		consumer.accept(Component.translatable("tooltip.twoheavens.kera_anvil"));
		if (!ShiftState.isDown() && anvilRecipe() != null) {
			consumer.accept(Component.translatable("tooltip.twoheavens.shift_for_recipe"));
		}
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ShiftState.isDown() ? Optional.ofNullable(anvilRecipe()) : Optional.empty();
	}

	private static RecipeTooltipData anvilRecipe() {
		return ModRecipeTooltips.madeFrom(ModBlocks.SMITHING_ANVIL.asItem());
	}
}
