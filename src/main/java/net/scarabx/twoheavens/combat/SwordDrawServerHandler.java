package net.scarabx.twoheavens.combat;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.item.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative handler for the R draw/sheathe toggle. The client
 * still plays its own predicted local animation/item-swap immediately for
 * responsiveness (SwordDrawController), but this is what actually puts the
 * katana/wakizashi into the player's REAL inventory - without this, systems
 * that check the server's own held-item state (like SwordComboHandler)
 * would never see the swords as drawn at all.
 */
public class SwordDrawServerHandler {

	private static final Map<UUID, StoredItems> storedItems = new HashMap<>();

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(DrawSwordsPayload.TYPE, DrawSwordsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DrawSwordsPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> toggle(player));
		});
	}

	private static void toggle(ServerPlayer player) {
		// Deliberately not re-derived from current inventory contents each
		// call - that's racy if draw/sheathe packets arrive close together,
		// and can leave the swords stuck equipped. Presence in this map is
		// the single source of truth for "currently drawn" server-side.
		boolean currentlyDrawn = storedItems.containsKey(player.getUUID());

		if (!currentlyDrawn) {
			if (!TrinketsApi.getAttachment(player).isEquipped(ModItems.DAISHO_SAYA_OBI)) {
				return;
			}
			storedItems.put(player.getUUID(), new StoredItems(
					player.getMainHandItem().copy(), player.getOffhandItem().copy()));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KATANA));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.WAKIZASHI));
		} else {
			StoredItems stored = storedItems.remove(player.getUUID());
			player.setItemInHand(InteractionHand.MAIN_HAND, stored != null ? stored.mainHand() : ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, stored != null ? stored.offHand() : ItemStack.EMPTY);
		}
	}

	private record StoredItems(ItemStack mainHand, ItemStack offHand) {
	}
}
