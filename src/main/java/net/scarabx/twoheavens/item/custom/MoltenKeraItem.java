package net.scarabx.twoheavens.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.item.ItemRecipeTooltip;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The hot kera. Its own class purely so it can carry a tooltip: it used to be a
 * plain Item::new, which meant no instruction and not even the recipe prompt,
 * despite having an entry in ModRecipeTooltips.
 *
 * This is the step where guidance ran out - the player is holding something that
 * is cooling in their hands with no indication that an anvil is where it goes.
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
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}
}
