package net.scarabx.twoheavens.combat;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.item.ModItems;

/**
 * Server-authoritative handler for the R draw/sheathe toggle. Whether a
 * player is drawn lives entirely as a DrawnSwordsAttachment on the player
 * entity itself - not a separate server-memory map - so it automatically
 * persists across relogs AND full client/JVM restarts (attached data saves
 * with the entity's own NBT) and automatically syncs to the owning client on
 * every change and on join (no custom resync packets/timers needed). The
 * client still plays its own predicted local animation/item-swap immediately
 * for responsiveness (SwordDrawController), but this is what actually puts
 * the katana/wakizashi into the player's REAL inventory.
 */
public class SwordDrawServerHandler {

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(DrawSwordsPayload.TYPE, DrawSwordsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DrawSwordsPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> toggle(player));
		});

		// Death always forces the player out of drawn/combat mode, regardless
		// of keepInventory - that gamerule only decides whether items
		// survive, not whether you stay in a fighting stance. Fires right
		// before death is finalized, ahead of drop collection, so the fake
		// katana/wakizashi get swapped back to the real stored items before
		// they could otherwise get scooped up as their own separate
		// ground-item drops (with keepInventory off) - only the obi (and
		// whatever was really held before drawing) should ever drop, never
		// phantom swords. With keepInventory on nothing drops at all, but
		// the drawn state still clears the same way either way.
		ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
			DrawnSwordsAttachment.StoredItems stored = player.removeAttached(DrawnSwordsAttachment.TYPE);
			if (stored != null) {
				player.setItemInHand(InteractionHand.MAIN_HAND, stored.mainHand());
				player.setItemInHand(InteractionHand.OFF_HAND, stored.offHand());
			}
			return true;
		});

		// Without keepInventory, dying drops the whole inventory including
		// the Daisho Saya Obi itself - if that happened, there's no obi left
		// to sheathe the swords back into. Re-equip the fake swords on
		// respawn if still marked drawn (attachments survive death since
		// they're copied via .copyOnDeath() is NOT set here deliberately -
		// ALLOW_DEATH above already cleared it, so this only matters if the
		// obi somehow got re-equipped between death and respawn, which
		// doesn't normally happen - kept as a defensive no-op in that case).
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (newPlayer.hasAttached(DrawnSwordsAttachment.TYPE)
					&& TrinketsApi.getAttachment(newPlayer).isEquipped(ModItems.DAISHO_SAYA_OBI)) {
				newPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KATANA));
				newPlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.WAKIZASHI));
			}
		});
	}

	private static void toggle(ServerPlayer player) {
		// Deliberately not re-derived from current inventory contents each
		// call - that's racy if draw/sheathe packets arrive close together,
		// and can leave the swords stuck equipped. The attachment's presence
		// is the single source of truth for "currently drawn" server-side.
		DrawnSwordsAttachment.StoredItems current = player.getAttached(DrawnSwordsAttachment.TYPE);

		if (current == null) {
			if (!TrinketsApi.getAttachment(player).isEquipped(ModItems.DAISHO_SAYA_OBI)) {
				return;
			}
			player.setAttached(DrawnSwordsAttachment.TYPE, new DrawnSwordsAttachment.StoredItems(
					player.getMainHandItem().copy(), player.getOffhandItem().copy()));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KATANA));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.WAKIZASHI));
		} else {
			player.removeAttached(DrawnSwordsAttachment.TYPE);
			player.setItemInHand(InteractionHand.MAIN_HAND, current.mainHand());
			player.setItemInHand(InteractionHand.OFF_HAND, current.offHand());
		}
	}
}
