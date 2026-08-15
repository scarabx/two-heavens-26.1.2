package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.client.Minecraft;
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
 * Client-only toggle: draws/sheathes the katana and wakizashi from the
 * Daisho Saya Obi trinket, swapping the local player's main/off-hand items
 * in sync with the arm animation - katana first, wakizashi immediately
 * after. Cosmetic and client-side only - does not sync to other players or
 * the server, same scope as the rest of the player-animation foundation.
 */
public class SwordDrawController {

	// Ticks from the trigger of draw_swords.animation.json until each sword
	// should appear - matched to that animation's right_arm/left_arm arc
	// keyframes (20 ticks/second).
	private static final int DRAW_KATANA_DELAY_TICKS = 11;
	private static final int DRAW_WAKIZASHI_DELAY_TICKS = 14;

	private static final int SHEATHE_WAKIZASHI_DELAY_TICKS = 6;
	private static final int SHEATHE_KATANA_DELAY_TICKS = 17;

	private static boolean drawn = false;

	private static final Deque<PendingSwap> pending = new ArrayDeque<>();

	private static ItemStack storedMainHand = ItemStack.EMPTY;
	private static ItemStack storedOffHand = ItemStack.EMPTY;

	public static void toggle(Player player) {
		if (!pending.isEmpty()) {
			return;
		}
		if (!TrinketsApi.getAttachment(player).isEquipped(ModItems.DAISHO_SAYA_OBI)) {
			return;
		}

		if (!drawn) {
			storedMainHand = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
			storedOffHand = player.getItemInHand(InteractionHand.OFF_HAND).copy();

			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlayAndHold(TwoHeavensPlayerAnimation.getDrawSwordsAnimation()));

			pending.add(new PendingSwap(DRAW_KATANA_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KATANA))));
			pending.add(new PendingSwap(DRAW_WAKIZASHI_DELAY_TICKS - DRAW_KATANA_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.WAKIZASHI))));
			drawn = true;
		} else {
			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlay(TwoHeavensPlayerAnimation.getSheatheSwordsAnimation()));

			pending.add(new PendingSwap(SHEATHE_WAKIZASHI_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.OFF_HAND, storedOffHand)));
			pending.add(new PendingSwap(SHEATHE_KATANA_DELAY_TICKS - SHEATHE_WAKIZASHI_DELAY_TICKS, p ->
					p.setItemInHand(InteractionHand.MAIN_HAND, storedMainHand)));
			drawn = false;
		}
	}

	public static void tick(Minecraft client) {
		PendingSwap next = pending.peek();
		if (next == null || client.player == null) {
			return;
		}
		if (next.ticksRemaining-- > 0) {
			return;
		}
		pending.poll();

		Player player = client.player;
		next.action.accept(player);
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.7F, 1.6F);

		if (pending.isEmpty() && !drawn) {
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
