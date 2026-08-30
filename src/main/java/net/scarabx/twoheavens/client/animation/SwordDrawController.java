package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.scarabx.twoheavens.combat.DrawSwordsPayload;
import net.scarabx.twoheavens.combat.DrawTiming;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import net.scarabx.twoheavens.combat.ObiSwords;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.item.ModItems;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Draws/sheathes the katana and wakizashi from the Daisho Obi trinket.
 * "Drawn" is server-authoritative via DrawnSwordsAttachment on the player
 * entity, synced to the client automatically by Fabric's attachment system.
 * That sync always lags our own local action by a tick or more, even on an
 * integrated server - predictedDrawn tracks our own local intent, and
 * awaitingServerConfirmation suppresses the external-change detector until
 * the server's value has actually caught up to what we predicted at least
 * once. Without that suppression window, the detector reads the sync lag
 * itself as an "external" flip-flop (false, because it hasn't arrived yet,
 * then true once it does) and re-triggers the pose mid-animation reacting to
 * its own echo.
 */
public class SwordDrawController {

	private static boolean predictedDrawn = false;
	private static boolean awaitingServerConfirmation = false;
	/**
	 * How long to keep suppressing the external-change detector before giving up.
	 * Without this, a toggle the server never acts on - a dropped packet, or a
	 * rejection - leaves the suppression latched on forever, so the client can never
	 * notice it has diverged and never resyncs.
	 */
	private static final int CONFIRMATION_TIMEOUT_TICKS = 40;
	private static int confirmationWaitTicks = 0;

	public static boolean isDrawn(Player player) {
		return player.hasAttached(DrawnSwordsAttachment.TYPE);
	}

	private static final Deque<PendingSwap> pending = new ArrayDeque<>();

	/** Writes to the slot the draw began from, never to whatever is selected now. */
	private static void putInDrawSlot(Player player, ItemStack stack) {
		if (storedMainHandSlot < 0) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			return;
		}
		player.getInventory().setItem(storedMainHandSlot, stack);
	}

	/**
	 * The sheathe half of the same write, and the reason it needs its own method:
	 * putting the sword back must never destroy something that arrived while drawn.
	 *
	 * The server relocates it - free slot first, then anywhere, then the floor. The
	 * client cannot copy that, because guessing a different slot here and guessing
	 * wrong leaves a phantom item on screen until the next container sync, which is
	 * worse than the gap it was trying to hide. So when the slot is genuinely
	 * occupied, this predicts NOTHING and lets the authoritative update land.
	 *
	 * A fake sword counts as free space, which is the ordinary case - the fake this
	 * very swap is replacing - so a normal sheathe still predicts instantly and none
	 * of this is reachable in play. Only the collision skips prediction, and losing
	 * smoothness for one frame there is the correct trade against showing an item
	 * being deleted.
	 */
	private static void restoreIntoDrawSlot(Player player, ItemStack stack) {
		if (storedMainHandSlot < 0) {
			return;
		}
		ItemStack occupant = player.getInventory().getItem(storedMainHandSlot);
		if (occupant.isEmpty() || ObiSwords.isFromObi(occupant)) {
			player.getInventory().setItem(storedMainHandSlot, stack);
		}
	}

	/** Same rule for the offhand, whose restore had the identical unconditional write. */
	private static void restoreOffHand(Player player, ItemStack stack) {
		ItemStack occupant = player.getItemInHand(InteractionHand.OFF_HAND);
		if (occupant.isEmpty() || ObiSwords.isFromObi(occupant)) {
			player.setItemInHand(InteractionHand.OFF_HAND, stack);
		}
	}

	private static ItemStack storedMainHand = ItemStack.EMPTY;
	private static ItemStack storedOffHand = ItemStack.EMPTY;

	/**
	 * The hotbar slot the draw began from.
	 *
	 * Every swap here is DELAYED, and setItemInHand targets whatever slot is selected
	 * when it finally fires - not the one the draw started from. Switching is blocked
	 * while drawn, but that block is gated on an attachment synced from the server, so
	 * there is a sub-second gap where a scroll gets through (wider with ping). The
	 * sheathe swap is the worse of the two at 17 ticks: it wrote the stored sword into
	 * whatever slot the player had moved to, destroying what was there.
	 *
	 * The server records this too - both sides must, or they disagree about where the
	 * sword went.
	 */
	private static int storedMainHandSlot = -1;

	public static void toggle(Player player) {
		if (!pending.isEmpty()) {
			return;
		}
		if (!TrinketsApi.getAttachment(player).isEquipped(ModItems.DAISHO_OBI)) {
			return;
		}

		ClientPlayNetworking.send(new DrawSwordsPayload());

		if (!predictedDrawn) {
			storedMainHand = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
			storedMainHandSlot = player.getInventory().getSelectedSlot();
			storedOffHand = player.getItemInHand(InteractionHand.OFF_HAND).copy();

			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlayAndHold(TwoHeavensPlayerAnimation.getDrawSwordsAnimation()));

			pending.add(new PendingSwap(DrawTiming.DRAW_KATANA_DELAY_TICKS, p ->
					putInDrawSlot(p, ObiSwords.issuedKatana())));
			pending.add(new PendingSwap(DrawTiming.DRAW_WAKIZASHI_DELAY_TICKS - DrawTiming.DRAW_KATANA_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.OFF_HAND, ObiSwords.issuedWakizashi())));
			AttackSwingController.resetAttackPose();
			predictedDrawn = true;
		} else {
			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlay(TwoHeavensPlayerAnimation.getSheatheSwordsAnimation()));

			pending.add(new PendingSwap(DrawTiming.SHEATHE_WAKIZASHI_DELAY_TICKS, p ->
					restoreOffHand(p, storedOffHand)));
			pending.add(new PendingSwap(DrawTiming.SHEATHE_KATANA_DELAY_TICKS - DrawTiming.SHEATHE_WAKIZASHI_DELAY_TICKS, p ->
					restoreIntoDrawSlot(p, storedMainHand)));
			predictedDrawn = false;
		}
		awaitingServerConfirmation = true;
		confirmationWaitTicks = 0;
	}

	public static void tick(Minecraft client) {
		Player player = client.player;
		if (player == null) {
			return;
		}

		boolean serverDrawn = isDrawn(player);
		if (awaitingServerConfirmation) {
			// Ignore mismatches entirely while waiting - they're just our
			// own action's sync still in flight, not a real external change.
			if (serverDrawn == predictedDrawn) {
				awaitingServerConfirmation = false;
			} else if (++confirmationWaitTicks >= CONFIRMATION_TIMEOUT_TICKS) {
				// The server never came back with our state. Stop suppressing and let
				// the correction below put us back in step on the next tick.
				awaitingServerConfirmation = false;
				pending.clear();
			}
		} else if (serverDrawn != predictedDrawn) {
			// A genuine external change (join, respawn, death forcing
			// sheathe, or correcting a real desync) - not something we
			// initiated locally.
			if (serverDrawn) {
				PlayerHandAnimator.trigger(player,
						RawAnimation.begin().thenPlayAndHold(TwoHeavensPlayerAnimation.getCombatIdleAnimation()));
				AttackSwingController.resetAttackPose();
			}
			predictedDrawn = serverDrawn;
		}

		PendingSwap next = pending.peek();
		if (next == null) {
			return;
		}
		if (next.ticksRemaining-- > 0) {
			return;
		}
		pending.poll();

		next.action.accept(player);
		// ARMOR_EQUIP_CHAIN is leather and buckles - it read as unpacking a backpack,
		// which is the wrong material for a blade leaving a saya. CHAIN_PLACE is metal,
		// and pitch does the rest: high and bright drawing, lower and softer sheathing,
		// so one sample reads as one action and its reverse.
		//
		// Played here rather than server-side on purpose - this is predictive, so it
		// lands the instant the swap does instead of a round trip later. Adding a second
		// server-side sound just doubled it.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS,
				predictedDrawn ? 0.7F : 0.55F, predictedDrawn ? 1.7F : 1.1F);

		if (pending.isEmpty() && !predictedDrawn) {
			storedMainHand = ItemStack.EMPTY;
			storedOffHand = ItemStack.EMPTY;
			storedMainHandSlot = -1;
		}
	}

	private static final class PendingSwap {
		int ticksRemaining;
		final Consumer<Player> action;

		PendingSwap(int ticksRemaining, Consumer<Player> action) {
			this.ticksRemaining = ticksRemaining;
			this.action = action;
		}
	}
}
