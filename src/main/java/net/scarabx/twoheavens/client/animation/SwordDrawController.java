package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.scarabx.twoheavens.combat.DrawSwordsPayload;
import net.scarabx.twoheavens.combat.DrawTiming;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import net.scarabx.twoheavens.combat.FakeDrawnSword;
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

	private static ItemStack storedMainHand = ItemStack.EMPTY;
	private static ItemStack storedOffHand = ItemStack.EMPTY;

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
			storedOffHand = player.getItemInHand(InteractionHand.OFF_HAND).copy();

			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlayAndHold(TwoHeavensPlayerAnimation.getDrawSwordsAnimation()));

			pending.add(new PendingSwap(DrawTiming.DRAW_KATANA_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.MAIN_HAND, FakeDrawnSword.katana())));
			pending.add(new PendingSwap(DrawTiming.DRAW_WAKIZASHI_DELAY_TICKS - DrawTiming.DRAW_KATANA_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.OFF_HAND, FakeDrawnSword.wakizashi())));
			AttackSwingController.resetAttackPose();
			predictedDrawn = true;
		} else {
			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlay(TwoHeavensPlayerAnimation.getSheatheSwordsAnimation()));

			pending.add(new PendingSwap(DrawTiming.SHEATHE_WAKIZASHI_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.OFF_HAND, storedOffHand)));
			pending.add(new PendingSwap(DrawTiming.SHEATHE_KATANA_DELAY_TICKS - DrawTiming.SHEATHE_WAKIZASHI_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.MAIN_HAND, storedMainHand)));
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
