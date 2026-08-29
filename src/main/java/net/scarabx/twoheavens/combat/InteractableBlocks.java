package net.scarabx.twoheavens.combat;

import java.util.Set;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks that answer a right-click, for deciding when a sword move is really a
 * block interaction.
 *
 * Minecraft has no "is this interactable" query - every block decides inside its own
 * use method, after the click, so nothing can be asked beforehand. Two signals get
 * most of the way there:
 *
 *   - A BLOCK ENTITY. Chests, furnaces, the tatara furnaces, the anvil display, and
 *     most containers.
 *   - A short list for the ones that answer a click with no block entity at all,
 *     of which the crafting table is the obvious one.
 *
 * Shared by the client and server checks deliberately: if the two disagree you get
 * an animation with no sweep behind it, or a sweep with no animation.
 */
public final class InteractableBlocks {

	/** Interactable, but with no block entity to give them away. */
	private static final Set<Block> NO_BLOCK_ENTITY = Set.of(
			Blocks.CRAFTING_TABLE,
			Blocks.STONECUTTER,
			Blocks.CARTOGRAPHY_TABLE,
			Blocks.SMITHING_TABLE,
			Blocks.LOOM,
			Blocks.GRINDSTONE,
			Blocks.COMPOSTER,
			Blocks.CAULDRON,
			Blocks.WATER_CAULDRON,
			Blocks.LAVA_CAULDRON,
			Blocks.POWDER_SNOW_CAULDRON,
			Blocks.LEVER,
			Blocks.REPEATER,
			Blocks.COMPARATOR,
			Blocks.DAYLIGHT_DETECTOR,
			Blocks.NOTE_BLOCK,
			Blocks.RESPAWN_ANCHOR,
			Blocks.CAKE,
			Blocks.DRAGON_EGG);

	private InteractableBlocks() {
	}

	public static boolean answersClick(BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) != null) {
			return true;
		}
		BlockState state = level.getBlockState(pos);
		return NO_BLOCK_ENTITY.contains(state.getBlock())
				|| state.is(BlockTags.DOORS)
				|| state.is(BlockTags.TRAPDOORS)
				|| state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.BUTTONS)
				|| state.is(BlockTags.BEDS)
				|| state.is(BlockTags.CANDLES)
				|| state.is(BlockTags.FLOWER_POTS);
	}
}
