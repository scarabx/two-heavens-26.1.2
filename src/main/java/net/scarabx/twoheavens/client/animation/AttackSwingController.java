package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.scarabx.twoheavens.item.ModItems;

/**
 * Client-only combat visuals, split the same way the mechanics are:
 *
 * - Drawn from a Daisho Obi: the full two-sword set. A left-click swing plays the
 *   wakizashi lunge/stab, and a right-click plays the paired katana slice.
 * - Katana alone, no obi: the solo slice only, using the one-handed variant. The
 *   paired animation holds the left arm in the offhand wakizashi pose, which looks
 *   wrong on an empty hand.
 *
 * These conditions deliberately mirror SwordComboHandler's. Previously the visuals
 * gated on drawn while the mechanics gated on held items, so a katana used without
 * an obi dealt its solo damage with no animation at all - the mod's signature move
 * firing invisibly, which read as a plain vanilla sword.
 *
 * Original note: while the katana/wakizashi are drawn
 * (SwordDrawController), any left-click swing plays a wakizashi lunge/stab
 * - no entity needs to be in range, so the move can be tested/tuned freely.
 * If a right-click follows within the combo window (only opening once the
 * stab has actually finished playing), it plays a katana slice as the
 * follow-up, same deal. Both moves return to combat_idle once they finish
 * instead of holding their own end pose. The actual functional stun/damage
 * (SwordComboHandler, server-side) still only ever triggers on a real hit
 * - that's a separate system and unaffected by this.
 */
public class AttackSwingController {

	// Matches attack_swing.animation.json's length (0.2s) - the finisher
	// can't start until the stab has actually finished playing, not
	// mid-swing.
	private static final int STAB_ANIMATION_TICKS = 4;
	private static final int COMBO_WINDOW_TICKS = 60;

	private static boolean lastSwinging = false;
	private static boolean lastUseDown = false;
	private static int comboReadyTick = -1;
	private static int comboExpireTick = -1;

	// Called whenever combat_idle becomes authoritative again (fresh draw,
	// or SwordDrawController re-applying the pose after detecting the synced
	// drawn attachment coming on).
	public static void resetAttackPose() {
		comboReadyTick = -1;
		comboExpireTick = -1;
	}

	public static void tick(Minecraft client) {
		Player player = client.player;
		if (player == null) {
			return;
		}

		boolean drawn = SwordDrawController.isDrawn(player);
		boolean holdingKatana = player.getMainHandItem().getItem() == ModItems.KATANA;

		boolean swinging = player.swinging;
		boolean newSwing = swinging && !lastSwinging;
		lastSwinging = swinging;

		if (newSwing && drawn) {
			// Plays the stab, then eases back to combat_idle via its own
			// dedicated return animation instead of snapping straight into
			// combat_idle's near-instant single frame.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getAttackSwingAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getAttackSwingReturnAnimation()));
			comboReadyTick = player.tickCount + STAB_ANIMATION_TICKS;
			comboExpireTick = comboReadyTick + COMBO_WINDOW_TICKS;
		}

		boolean useDown = client.options.keyUse.isDown();
		boolean useJustPressed = useDown && !lastUseDown;
		lastUseDown = useDown;

		// Same slice, same animation whether it's landing as the paired
		// finisher after a wakizashi stab or on its own with no stab in
		// progress - the katana works standalone too (the functional
		// server-side difference between the two is handled entirely by
		// SwordComboHandler, not here).
		if (useJustPressed && drawn) {
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getKatanaSliceAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getKatanaSliceReturnAnimation()));
			comboReadyTick = -1;
			comboExpireTick = -1;
		} else if (useJustPressed && holdingKatana) {
			// Undrawn: the solo slice still lands (SwordComboHandler does not gate it),
			// so it gets the one-handed animation rather than nothing.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getKatanaSliceSoloAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getKatanaSliceSoloReturnAnimation()));
		}
	}
}
