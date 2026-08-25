package net.scarabx.twoheavens.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Block item for this mod's blocks, so Kera and Tatara Clay Block can carry the
 * recipe tooltip through a normal override rather than needing the vanilla mixin.
 */
public class ModBlockItem extends BlockItem {

	public ModBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}
}
