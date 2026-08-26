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
 * Tongs had no class at all - a plain Item::new - so they showed nothing on hover
 * despite having a recipe registered. They also carry the one rule the mod never
 * stated anywhere: a hot blade burns you unless these are in your off hand.
 */
public class TongsItem extends Item {

	public TongsItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		consumer.accept(Component.translatable("tooltip.twoheavens.tongs_use"));
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}
}
