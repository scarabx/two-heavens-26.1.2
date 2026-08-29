package net.scarabx.twoheavens.combat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.scarabx.twoheavens.item.ModItems;

/**
 * A katana or wakizashi in the main hand does not mine, and does not sweep at
 * blocks.
 *
 * These are not tools - they carry no Tool component and no mining attributes, so
 * they were never efficient at breaking anything, but vanilla still let them chip
 * away at blocks and still played the swing. Two problems with that: a blade takes
 * durability from stone for no benefit, and the sweep effect fired on every
 * left-click at terrain, announcing an attack that never happened.
 *
 * Left-clicking a block is now refused outright. The refusal also records the tick
 * it happened on, which is what lets the swing mixin tell a block click apart from
 * a swing at air - LivingEntity#swing has no idea what was aimed at.
 */
public final class SwordBlockGuard {

	/** Players whose current swing was aimed at a block, cleared as it is read. */
	private static final Set<UUID> swungAtBlock = ConcurrentHashMap.newKeySet();

	private SwordBlockGuard() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (hand != InteractionHand.MAIN_HAND || !isBlade(player.getItemInHand(hand).getItem())) {
				return InteractionResult.PASS;
			}
			swungAtBlock.add(player.getUUID());
			// FAIL rather than SUCCESS: SUCCESS would count as a handled interaction and
			// still swing the arm, which is the animation we are trying to suppress.
			return InteractionResult.FAIL;
		});
	}

	private static boolean isBlade(Item item) {
		return item == ModItems.KATANA || item == ModItems.WAKIZASHI;
	}

	/** True once per block click, consumed by the caller. */
	public static boolean consumeBlockSwing(Player player) {
		return swungAtBlock.remove(player.getUUID());
	}
}
