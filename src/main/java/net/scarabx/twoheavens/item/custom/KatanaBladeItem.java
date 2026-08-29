package net.scarabx.twoheavens.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.item.ItemRecipeTooltip;
import net.scarabx.twoheavens.item.ModRecipeTooltips;
import net.scarabx.twoheavens.item.ShiftState;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.world.item.Item;

public class KatanaBladeItem extends Item {

	public KatanaBladeItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	/**
	 * Shows the whole step, not just this item: the katana recipe, then how to make the
	 * tsuba and the tsuka it names.
	 *
	 * Those two parts are the point. The katana grid names them at the moment they
	 * become relevant, and **you cannot hover an item you do not own** - so without
	 * this the tooltip told you what you needed and gave you no way to find out how to
	 * get it. Same gap the Hot Kera has with the Smithing Anvil, same fix.
	 */
	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		if (!ShiftState.isDown()) {
			return Optional.empty();
		}
		return Optional.ofNullable(ModRecipeTooltips.relatedTo(stack.getItem()));
	}
}
