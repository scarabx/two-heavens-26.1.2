package net.scarabx.twoheavens.combat;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.item.ModItems;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-authoritative handler for the R draw/sheathe toggle. Whether a
 * player is drawn lives entirely as a DrawnSwordsAttachment on the player
 * entity itself - not a separate server-memory map - so it automatically
 * persists across relogs AND full client/JVM restarts (attached data saves
 * with the entity's own NBT) and automatically syncs to the owning client on
 * every change and on join (no custom resync packets/timers needed).
 *
 * The actual item swaps (setItemInHand) are delayed to match
 * DrawTiming - the same constants the client's local visual prediction
 * (SwordDrawController) uses. This used to happen instantly here, which
 * meant the server's real, authoritative inventory change reached back and
 * overrode the client's own delayed local prediction almost immediately (on
 * an integrated/singleplayer server especially) - the katana/wakizashi were
 * appearing the instant R was pressed regardless of what delay the client
 * was told to use, since the client's local timing was never actually the
 * bottleneck.
 */
public class SwordDrawServerHandler {

	private static final Map<UUID, Deque<PendingSwap>> pendingSwaps = new HashMap<>();

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(DrawSwordsPayload.TYPE, DrawSwordsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DrawSwordsPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> toggle(player));
		});

		ServerTickEvents.END_SERVER_TICK.register(SwordDrawServerHandler::onServerTick);

		// Death always forces the player out of drawn/combat mode, regardless
		// of keepInventory - that gamerule only decides whether items
		// survive, not whether you stay in a fighting stance. Fires right
		// before death is finalized, ahead of drop collection, so the fake
		// katana/wakizashi get swapped back to the real stored items before
		// they could otherwise get scooped up as their own separate
		// ground-item drops (with keepInventory off) - only the obi (and
		// whatever was really held before drawing) should ever drop, never
		// phantom swords. With keepInventory on nothing drops at all, but
		// the drawn state still clears the same way either way. This is a
		// forced, instant restore - no animation to wait for, and any
		// still-pending delayed swap for this player is cancelled so it
		// can't fire afterward and re-apply a fake sword post-mortem.
		ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
			pendingSwaps.remove(player.getUUID());
			DrawnSwordsAttachment.StoredItems stored = player.removeAttached(DrawnSwordsAttachment.TYPE);
			if (stored != null) {
				restoreStoredItems(player, stored);
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
		// Instant, same as death - there's no draw animation playing here.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (newPlayer.hasAttached(DrawnSwordsAttachment.TYPE)
					&& TrinketsApi.getAttachment(newPlayer).isEquipped(ModItems.DAISHO_SAYA_OBI)) {
				newPlayer.setItemInHand(InteractionHand.MAIN_HAND, FakeDrawnSword.katana());
				newPlayer.setItemInHand(InteractionHand.OFF_HAND, FakeDrawnSword.wakizashi());
			}
		});
	}

	private static void onServerTick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Deque<PendingSwap>>> iterator = pendingSwaps.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Deque<PendingSwap>> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			Deque<PendingSwap> queue = entry.getValue();
			while (!queue.isEmpty() && queue.peek().applyTick() <= player.tickCount) {
				queue.poll().action().accept(player);
			}
			if (queue.isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static void toggle(ServerPlayer player) {
		// Ignore a new toggle while a previous draw/sheathe still has delayed
		// swaps in flight - mirrors the client's own `pending.isEmpty()`
		// guard, which normally stops it from even sending this packet
		// again mid-animation, but this keeps the server safe against that
		// too (reordered packets, a modified client, etc.).
		if (pendingSwaps.containsKey(player.getUUID())) {
			return;
		}

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
					player.getInventory().getSelectedSlot(),
					player.getMainHandItem().copy(), player.getOffhandItem().copy()));

			Deque<PendingSwap> queue = new ArrayDeque<>();
			queue.add(new PendingSwap(player.tickCount + DrawTiming.DRAW_KATANA_DELAY_TICKS,
					p -> p.setItemInHand(InteractionHand.MAIN_HAND, FakeDrawnSword.katana())));
			queue.add(new PendingSwap(player.tickCount + DrawTiming.DRAW_WAKIZASHI_DELAY_TICKS,
					p -> p.setItemInHand(InteractionHand.OFF_HAND, FakeDrawnSword.wakizashi())));
			pendingSwaps.put(player.getUUID(), queue);
		} else {
			player.removeAttached(DrawnSwordsAttachment.TYPE);

			Deque<PendingSwap> queue = new ArrayDeque<>();
			queue.add(new PendingSwap(player.tickCount + DrawTiming.SHEATHE_WAKIZASHI_DELAY_TICKS,
					p -> restoreOffHand(p, current)));
			queue.add(new PendingSwap(player.tickCount + DrawTiming.SHEATHE_KATANA_DELAY_TICKS,
					p -> restoreMainHand(p, current)));
			pendingSwaps.put(player.getUUID(), queue);
		}
	}

	// Sheathe must always fully clear the fake katana/wakizashi no matter
	// where in the inventory the player moved them to - relying on them
	// still being in main/off hand left stragglers behind (never removed,
	// and the stored real items would overwrite whatever was actually in
	// main/off hand at the time instead of restoring cleanly). Slot
	// switching while drawn is separately blocked (client + server mixins),
	// so this is mostly a defensive sweep now, but still covers drag/drop
	// within the inventory screen itself.
	private static void stripFakeSwords(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (FakeDrawnSword.isFake(inventory.getItem(i))) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	private static void restoreOffHand(ServerPlayer player, DrawnSwordsAttachment.StoredItems stored) {
		stripFakeSwords(player.getInventory());
		player.setItemInHand(InteractionHand.OFF_HAND, stored.offHand());
	}

	// The stored real main-hand item is written back to the EXACT hotbar
	// slot it was pulled from at draw time (StoredItems.mainHandSlot), not
	// "whatever slot is currently selected" - if those two ever drifted
	// apart, the real item would land in the wrong slot while a stray real
	// katana/wakizashi from a previous draw cycle sat untouched in the old
	// slot, making the next draw look like it conjured a third sword.
	private static void restoreMainHand(ServerPlayer player, DrawnSwordsAttachment.StoredItems stored) {
		stripFakeSwords(player.getInventory());
		Inventory inventory = player.getInventory();
		inventory.setSelectedSlot(stored.mainHandSlot());
		inventory.setItem(stored.mainHandSlot(), stored.mainHand());
	}

	// Used only by the instant/forced paths (death) - no animation to wait
	// for there, so both hands restore together immediately.
	private static void restoreStoredItems(ServerPlayer player, DrawnSwordsAttachment.StoredItems stored) {
		stripFakeSwords(player.getInventory());
		Inventory inventory = player.getInventory();
		inventory.setSelectedSlot(stored.mainHandSlot());
		inventory.setItem(stored.mainHandSlot(), stored.mainHand());
		player.setItemInHand(InteractionHand.OFF_HAND, stored.offHand());
	}

	private record PendingSwap(int applyTick, Consumer<ServerPlayer> action) {
	}
}
