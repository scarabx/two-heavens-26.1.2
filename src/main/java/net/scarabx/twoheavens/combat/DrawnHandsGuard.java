package net.scarabx.twoheavens.combat;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.scarabx.twoheavens.event.JoinMessageHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A drawn player has both hands on their swords, so they cannot open storage.
 *
 * This is a STANCE rule, not an anti-duplication one - duplication is already
 * impossible, because the obi issues real swords and knows they are gone
 * (`ObiSwords`). Nothing here is load-bearing for correctness; it exists because
 * drawn is a fighting mode, and rummaging through a chest mid-guard reads wrong
 * next to a hotbar that is already locked for the same reason.
 *
 * **Blocks the OPEN, never the contents.** An earlier attempt let the screen open
 * and then refused every click inside it, which reads as a broken mod rather than a
 * rule - the player is given a working-looking interface that ignores them. A chest
 * that does not open, and says why, is a rule. That distinction is the entire reason
 * this version is acceptable and that one was reverted.
 *
 * Deliberately NARROW in three ways:
 *
 *   - Screens only, via `opensAScreen`, NOT `answersClick`. Doors, trapdoors, fence
 *     gates, buttons and levers keep working - fleeing through a door with your
 *     swords out is exactly the fantasy, and blocking it would be the opposite.
 *   - Item frames and armour stands only, of all entities. Horses, villagers, boats
 *     and leads are untouched; those are movement and conversation, not storage.
 *   - Your OWN inventory is untouched. It is not a block, nothing about it is
 *     unrealistic mid-fight, and with the swords real there is nothing to protect.
 */
public final class DrawnHandsGuard {

	private static final Map<UUID, Integer> lastTold = new HashMap<>();
	private static final int MESSAGE_COOLDOWN_TICKS = 60;

	private DrawnHandsGuard() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!isDrawn(player) || !InteractableBlocks.opensAScreen(level, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}
			tell(player);
			return InteractionResult.FAIL;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!isDrawn(player) || !(entity instanceof ItemFrame || entity instanceof ArmorStand)) {
				return InteractionResult.PASS;
			}
			tell(player);
			return InteractionResult.FAIL;
		});
	}

	private static boolean isDrawn(Player player) {
		return player.hasAttached(DrawnSwordsAttachment.TYPE);
	}

	/**
	 * Chat with the ping, like every other message that fires because something just
	 * happened, and on a cooldown because - unlike the one-time pointers - this one can
	 * fire on every click and would otherwise bury them.
	 *
	 * "Both hands are on your blades" states the reason instead of threatening a
	 * consequence, and the reason is the whole rule - the stance is the thing stopping
	 * you, not a penalty. Drafts that warned about damaging your belongings were
	 * dropped: nothing is at risk now that the swords are real items, and a message
	 * hinting at a mechanic that does not exist costs more than it earns.
	 */
	private static void tell(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		Integer last = lastTold.get(serverPlayer.getUUID());
		if (last != null && serverPlayer.tickCount - last < MESSAGE_COOLDOWN_TICKS) {
			return;
		}
		lastTold.put(serverPlayer.getUUID(), serverPlayer.tickCount);
		serverPlayer.sendSystemMessage(Component.translatable("message.twoheavens.hands_full")
				.withStyle(ChatFormatting.GOLD));
		JoinMessageHandler.ping(serverPlayer);
	}
}
