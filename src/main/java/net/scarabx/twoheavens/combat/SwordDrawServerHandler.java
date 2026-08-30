package net.scarabx.twoheavens.combat;

import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

		// Item frames and armour stands TAKE the held stack on a right-click, and that
		// never goes through a container menu, so the slot guard cannot see it. A frame
		// given a fake katana keeps a working one once the real sword is restored -
		// stripFakeSwords only ever sweeps the inventory, never an entity.
		//
		// Scoped to the fake STACK, like the slot guard: the other hand can hold
		// anything and there is no reason that should not go in a frame. FAIL rather
		// than PASS, since PASS falls through to the vanilla behaviour being stopped.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!FakeDrawnSword.isFake(player.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			// Same line the slot guard uses, for the same reason: a frame that takes
			// nothing and says nothing reads as broken. It also answers the question
			// behind the attempt - someone framing a sword wants to DISPLAY it, and the
			// Daisho Saya is the better display piece anyway, being the pair as one item.
			player.sendOverlayMessage(
					Component.translatable("message.twoheavens.sheathe_to_store")
							.withStyle(ChatFormatting.GOLD));
			return InteractionResult.FAIL;
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
		// the Daisho Obi itself - if that happened, there's no obi left
		// to sheathe the swords back into. Re-equip the fake swords on
		// respawn if still marked drawn (attachments survive death since
		// they're copied via .copyOnDeath() is NOT set here deliberately -
		// ALLOW_DEATH above already cleared it, so this only matters if the
		// obi somehow got re-equipped between death and respawn, which
		// doesn't normally happen - kept as a defensive no-op in that case).
		// Instant, same as death - there's no draw animation playing here.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (newPlayer.hasAttached(DrawnSwordsAttachment.TYPE)
					&& TrinketsApi.getAttachment(newPlayer).isEquipped(ModItems.DAISHO_OBI)) {
				newPlayer.setItemInHand(InteractionHand.MAIN_HAND, FakeDrawnSword.katana());
				newPlayer.setItemInHand(InteractionHand.OFF_HAND, FakeDrawnSword.wakizashi());
			}
		});
	}

	private static void onServerTick(MinecraftServer server) {
		// Starting the tutorial needs no event: Trinkets has no equip callback here, and
		// a cheap per-player check on the tick we already run is simpler than mixing into
		// its slot handling. Only ever fires once, since advance() moves off NOT_STARTED.
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (CombatTutorialAttachment.step(online) == CombatTutorialAttachment.NOT_STARTED
					&& TrinketsApi.getAttachment(online).isEquipped(ModItems.DAISHO_OBI)) {
				CombatTutorialAttachment.advance(online,
						CombatTutorialAttachment.NOT_STARTED, CombatTutorialAttachment.DRAW);
			}

			// A drawn player faces where they LOOK, not where they walk - the same
			// alignment vanilla applies while aiming a bow. The animations are on body
			// bones, so without this the whole stance points along WASD.
			//
			// Server-side as well as client-side, and both are needed: the client fixes
			// what YOU see of yourself, this is what every OTHER player sees of you. With
			// only the client half, a drawn player would look correct to themselves and
			// face the wrong way to everyone else.
			if (online.hasAttached(DrawnSwordsAttachment.TYPE)) {
				online.yBodyRot = online.getYHeadRot();
				online.yBodyRotO = online.yHeadRotO;
			}
		}

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
		// A toggle arriving while swaps are still in flight must NOT be dropped.
		// The client's own `pending.isEmpty()` guard clears on ITS timeline, but the
		// server's queue starts a tick or more later (the packet is deferred through
		// server.execute, plus latency), so there is a window where the client will
		// happily send another toggle while this queue is still draining. Silently
		// returning there left the client predicting one state and the server holding
		// the other, with no way for the client to find out - the swords would then
		// get overwritten back into the player's hands by the authoritative inventory
		// sync, dropping them out of combat pose.
		//
		// Flushing instead means the server always ends up in the state the client
		// asked for. Spamming the key snaps through the animation, which is what the
		// client is doing visually anyway.
		Deque<PendingSwap> inFlight = pendingSwaps.remove(player.getUUID());
		if (inFlight != null) {
			while (!inFlight.isEmpty()) {
				inFlight.poll().action().accept(player);
			}
		}

		// Deliberately not re-derived from current inventory contents each
		// call - that's racy if draw/sheathe packets arrive close together,
		// and can leave the swords stuck equipped. The attachment's presence
		// is the single source of truth for "currently drawn" server-side.
		DrawnSwordsAttachment.StoredItems current = player.getAttached(DrawnSwordsAttachment.TYPE);

		if (current == null) {
			if (!TrinketsApi.getAttachment(player).isEquipped(ModItems.DAISHO_OBI)) {
				return;
			}
			player.setAttached(DrawnSwordsAttachment.TYPE, new DrawnSwordsAttachment.StoredItems(
					player.getInventory().getSelectedSlot(),
					player.getMainHandItem().copy(), player.getOffhandItem().copy()));
			CombatTutorialAttachment.advance(player,
					CombatTutorialAttachment.DRAW, CombatTutorialAttachment.STUN);

			Deque<PendingSwap> queue = new ArrayDeque<>();
			// Written to the RECORDED slot, never to "whatever is selected now".
			//
			// The swap is delayed, so the selection can move between pressing R and the
			// swap firing. setItemInHand writes to the current slot, so the fake katana
			// landed in whatever the player had switched to and destroyed what was there
			// - cooked beef, in the case that turned this up. Sheathing then stripped the
			// fake from that slot, leaving it empty, while the real katana went back to
			// the slot it came from.
			int drawSlot = player.getInventory().getSelectedSlot();
			queue.add(new PendingSwap(player.tickCount + DrawTiming.DRAW_KATANA_DELAY_TICKS,
					p -> p.getInventory().setItem(drawSlot, FakeDrawnSword.katana())));
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
	// still being in mainhand/offhand left stragglers behind (never removed,
	// and the stored real items would overwrite whatever was actually in
	// mainhand/offhand at the time instead of restoring cleanly). Slot
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

	// Deliberately does NOT run the stripFakeSwords sweep. This fires first, at the
	// wakizashi's keyframe, and the sweep clears EVERY fake sword - so running it here
	// also wiped the katana out of the mainhand, leaving that hand empty for the rest
	// of the animation until restoreMainHand put the real item back. Setting the
	// offhand alone replaces the fake wakizashi and leaves the katana on screen until
	// its own keyframe, which is what the animation is showing.
	private static void restoreOffHand(ServerPlayer player, DrawnSwordsAttachment.StoredItems stored) {
		if (stored.offHand().isEmpty()) {
			// Nothing was being held, so there is nothing to give back - but the fake
			// wakizashi still has to go, and an unconditional write of EMPTY here would
			// also wipe anything real that had arrived in the meantime.
			if (FakeDrawnSword.isFake(player.getItemInHand(InteractionHand.OFF_HAND))) {
				player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			}
			return;
		}
		if (isReplaceable(player.getItemInHand(InteractionHand.OFF_HAND))) {
			player.setItemInHand(InteractionHand.OFF_HAND, stored.offHand());
			return;
		}
		giveBackElsewhere(player, stored.offHand());
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
		restoreInto(player, stored.mainHandSlot(), stored.mainHand());
	}

	/**
	 * Puts a stored item back WITHOUT destroying whatever is in its place.
	 *
	 * The restore used to be an unconditional setItem, which assumed the slot it came
	 * from would still be free when it went back. It is not always: the fakes are
	 * stripped immediately before this runs, so anything left in that slot arrived
	 * DURING the draw, and writing over it destroyed a real item. Since the swords are
	 * usually drawn for a whole fight, that window is long.
	 *
	 * Free slot first so the item lands back exactly where it came from in the normal
	 * case - which is the whole point of recording the slot at draw time - then the
	 * rest of the inventory, then the floor. Dropping is the last resort but it is
	 * still a restore: the item exists and the player can see it, which nothing about
	 * overwriting could say.
	 */
	private static void restoreInto(ServerPlayer player, int slot, ItemStack stored) {
		if (stored.isEmpty()) {
			return;
		}
		Inventory inventory = player.getInventory();
		if (isReplaceable(inventory.getItem(slot))) {
			inventory.setItem(slot, stored);
			return;
		}
		giveBackElsewhere(player, stored);
	}

	// A fake sword counts as free space: it is ours, it is about to be removed anyway,
	// and treating it as occupied would send every ordinary sheathe down the fallback
	// path. Only a REAL item blocks the slot.
	private static boolean isReplaceable(ItemStack stack) {
		return stack.isEmpty() || FakeDrawnSword.isFake(stack);
	}

	private static void giveBackElsewhere(ServerPlayer player, ItemStack stored) {
		if (!player.getInventory().add(stored)) {
			player.drop(stored, false);
		}
	}

	// Used only by the instant/forced paths (death) - no animation to wait
	// for there, so both hands restore together immediately.
	private static void restoreStoredItems(ServerPlayer player, DrawnSwordsAttachment.StoredItems stored) {
		stripFakeSwords(player.getInventory());
		Inventory inventory = player.getInventory();
		inventory.setSelectedSlot(stored.mainHandSlot());
		restoreInto(player, stored.mainHandSlot(), stored.mainHand());
		restoreOffHand(player, stored);
	}

	private record PendingSwap(int applyTick, Consumer<ServerPlayer> action) {
	}
}
