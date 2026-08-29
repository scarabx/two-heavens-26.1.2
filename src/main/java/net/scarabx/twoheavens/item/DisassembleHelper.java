package net.scarabx.twoheavens.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
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

	/**
	 * The block-aimed path.
	 *
	 * Sneak-right-click is the gesture, and vanilla sets suppressUsingBlock whenever a
	 * player is sneaking with something in hand - so the block's own use is skipped and
	 * Item#useOn is called INSTEAD of Item#use. Overriding only use() meant disassembly
	 * worked solely while aiming at air or sky, which is not how anyone holds an item
	 * they are trying to take apart.
	 */
	public static InteractionResult splitInto(UseOnContext context, Item first, Item second) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		return splitInto(context.getLevel(), player, context.getHand(), first, second);
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
			// Hand back both parts FIRST, then consume - the order matters.
			//
			// Shrinking first empties the held slot, so Inventory#add sees it as free and
			// drops one of the parts straight into it. On the air-aimed path that is fatal:
			// ServerPlayerGameMode#useItem post-processes the hand afterwards, rewriting
			// that slot from bookkeeping captured BEFORE use() ran, and the part sitting
			// there is overwritten. useItemOn has no such step, which is exactly why
			// disassembling worked aiming at a block and lost an item aiming at air.
			//
			// Giving first means the saya still occupies the hand, so neither part can
			// land there and vanilla's rewrite has nothing of ours to destroy.
			give(player, new ItemStack(first));
			give(player, new ItemStack(second));
			held.shrink(1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					// The same sample the draw and sheathe use, at the sheathe's pitch.
					// ARMOR_EQUIP_CHAIN was leather and buckles - the wrong material - and
					// one metallic sample across all three keeps the swords sounding like
					// one set of objects rather than three unrelated interactions.
					SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7F, 1.1F);
		}

		return InteractionResult.SUCCESS;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
