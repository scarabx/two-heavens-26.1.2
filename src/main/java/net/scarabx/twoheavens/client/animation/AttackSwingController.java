package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.player.Player;
import net.scarabx.twoheavens.combat.WakizashiCutPayload;
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
	private static boolean lastAttackDown = false;
	private static boolean lastPickDown = false;
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
		boolean holdingWakizashi = player.getMainHandItem().getItem() == ModItems.WAKIZASHI;

		boolean swinging = player.swinging;
		boolean newSwing = swinging && !lastSwinging;
		lastSwinging = swinging;

		boolean attackDown = client.options.keyAttack.isDown();
		boolean attackJustPressed = attackDown && !lastAttackDown;
		lastAttackDown = attackDown;

		if (attackJustPressed && !drawn && holdingWakizashi) {
			// The wakizashi's no-obi move. Triggered off the key rather than
			// player.swinging, because vanilla's swing is now suppressed for an
			// undrawn wakizashi (UndrawnSwordAttackBlockMixin) - it was intermittently
			// beating our animation and showing vanilla's upward slice instead. That
			// also means the server never sees the attack, hence the payload.
			//
			// thenPlay, not thenPlayAndHold: undrawn there is no combat_idle stance to
			// settle into, and holding would leave this layer asserting a pose forever.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getWakizashiCutAnimation())
					.thenPlay(TwoHeavensPlayerAnimation.getWakizashiCutReturnAnimation()));

			int targetId = client.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
			ClientPlayNetworking.send(new WakizashiCutPayload(targetId));
		}

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

		// Middle-click while drawn: the wakizashi's cut as a third move, alongside the
		// stab on left and the katana on right. A direct binding rather than a mode -
		// left-click always means stab, with no state to remember mid-fight.
		//
		// Pick-block is safe to take over here: in survival it only reselects a hotbar
		// slot, and hotbar switching is already suppressed while drawn.
		boolean pickDown = client.options.keyPickItem.isDown();
		boolean pickJustPressed = pickDown && !lastPickDown;
		lastPickDown = pickDown;

		if (pickJustPressed && drawn) {
			// Drawn, the wakizashi sits in the off hand, so this is the mirrored
			// left-arm animation. thenPlayAndHold, unlike the undrawn cut: drawn there
			// IS a stance to settle back into.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getWakizashiCutOffhandAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getWakizashiCutOffhandReturnAnimation()));

			int cutTarget = client.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
			ClientPlayNetworking.send(new WakizashiCutPayload(cutTarget));
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
			//
			// thenPlay, NOT thenPlayAndHold. Holding is right when drawn, because
			// combat_idle is a real resting pose to settle into. Undrawn there is no
			// stance to hold, so holding left our layer asserting a pose forever while
			// vanilla tried to animate its own swing underneath - the two fought and
			// produced a small forward lurch on the next left-click. Letting the return
			// finish releases the arm back to vanilla.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getKatanaSliceSoloAnimation())
					.thenPlay(TwoHeavensPlayerAnimation.getKatanaSliceSoloReturnAnimation()));
		}
	}
}
