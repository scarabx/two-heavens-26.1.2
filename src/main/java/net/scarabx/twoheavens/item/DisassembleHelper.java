package net.scarabx.twoheavens.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sneak-right-click to take an assembled piece apart again.
 *
 * Assembly stays a crafting recipe - it is discoverable, it shows up in the recipe
 * book and in the mod's own recipe tooltips. Disassembly cannot be a recipe at all,
 * because a vanilla crafting recipe produces exactly one output stack and these give
 * back two different items, so it lives here instead.
 */
public final class DisassembleHelper {

	private DisassembleHelper() {
	}

	public static InteractionResult splitInto(Level level, Player player, InteractionHand hand,
											   Item first, Item second) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}

		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide()) {
			// Consume one, hand back both parts. Anything that will not fit is dropped
			// rather than silently lost - these cost a full smithing chain each.
			held.shrink(1);
			give(player, new ItemStack(first));
			give(player, new ItemStack(second));
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.8F, 1.2F);
		}

		return InteractionResult.SUCCESS;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
