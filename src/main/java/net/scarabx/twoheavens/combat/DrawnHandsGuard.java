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

	private DrawnHandsGuard() {
	}

	public static void register() {
		// SERVER ONLY, and this is not a detail. Both callbacks also fire on the client,
		// and a FAIL there cancels the interaction locally - the packet is never sent, so
		// the server never sees the click and never says anything. That is exactly how
		// the refusal ended up working silently: the block was right and the explanation
		// never ran. Passing on the client lets the click reach the server, which refuses
		// it and speaks. Nothing opens in the meantime, because a container screen only
		// appears when the server tells it to.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !isDrawn(player)
					|| !InteractableBlocks.opensAScreen(level, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}
			tell(player, hit.getBlockPos());
			return InteractionResult.FAIL;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !isDrawn(player)
					|| !(entity instanceof ItemFrame || entity instanceof ArmorStand)) {
				return InteractionResult.PASS;
			}
			tell(player, entity.getUUID());
			return InteractionResult.FAIL;
		});
	}

	private static boolean isDrawn(Player player) {
		return player.hasAttached(DrawnSwordsAttachment.TYPE);
	}

	/** Chat with the ping, every time. One attempt, one answer. */
	public static void tell(Player player, Object target) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.translatable("message.twoheavens.hands_full")
					.withStyle(ChatFormatting.GOLD));
			JoinMessageHandler.ping(serverPlayer);
		}
	}
}
