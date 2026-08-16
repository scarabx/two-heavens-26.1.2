package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only combo visuals: while the katana/wakizashi are drawn
 * (SwordDrawController), any left-click swing plays a wakizashi lunge/stab
 * - no entity needs to be in range, so the move can be tested/tuned freely.
 * If a right-click follows within COMBO_WINDOW_TICKS, it plays a katana
 * slice as the follow-up, same deal. The actual functional stun/damage
 * (SwordComboHandler, server-side) still only ever triggers on a real hit
 * - that's a separate system and unaffected by this.
 */
public class AttackSwingController {

	private static final int COMBO_WINDOW_TICKS = 20;

	private static boolean lastSwinging = false;
	private static boolean lastUseDown = false;
	private static int comboExpireTick = -1;

	public static void tick(Minecraft client) {
		Player player = client.player;
		if (player == null) {
			return;
		}

		boolean drawn = SwordDrawController.isDrawn();

		boolean swinging = player.swinging;
		boolean newSwing = swinging && !lastSwinging;
		lastSwinging = swinging;

		if (newSwing && drawn) {
			// Holds on the stab's own final frame (its extended/struck pose)
			// instead of chaining back to combat_idle - tuning this move to
			// stop and stay at its end position, not snap back to rest.
			PlayerHandAnimator.trigger(player,
					RawAnimation.begin().thenPlayAndHold(TwoHeavensPlayerAnimation.getAttackSwingAnimation()));
			comboExpireTick = player.tickCount + COMBO_WINDOW_TICKS;
		}

		boolean useDown = client.options.keyUse.isDown();
		boolean useJustPressed = useDown && !lastUseDown;
		lastUseDown = useDown;

		if (useJustPressed && drawn && player.tickCount <= comboExpireTick) {
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getKatanaSliceAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getCombatIdleAnimation()));
			comboExpireTick = -1;
		}
	}
}
