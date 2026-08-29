package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranimcore.animation.RawAnimation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.player.Player;
import net.scarabx.twoheavens.combat.MovePayload;
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

	/** Whatever is under the crosshair, or -1 - the server acquires its own if none. */
	/**
	 * True when the crosshair is on a block that answers a click.
	 *
	 * Approximated by "has a BLOCK ENTITY" - Minecraft has no query for interactable,
	 * since each block decides inside its own use method after the click. That covers
	 * chests, furnaces, the tatara furnaces and the anvil, and leaves plain terrain
	 * alone so facing a wall still lets you swing. Known gap: a crafting table has no
	 * block entity, nor do levers, doors and buttons.
	 */
	private static boolean interactingWithBlock(Minecraft client) {
		if (client.level == null
				|| !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) {
			return false;
		}
		return client.level.getBlockEntity(hit.getBlockPos()) != null;
	}

	private static int aimedEntityId(Minecraft client) {
		return client.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
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

		if (attackJustPressed && !drawn && holdingWakizashi && !interactingWithBlock(client)) {
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

			ClientPlayNetworking.send(new MovePayload(MovePayload.CUT, aimedEntityId(client)));
		}

		if (newSwing && drawn && !interactingWithBlock(client)) {
			// Plays the stab, then eases back to combat_idle via its own
			// dedicated return animation instead of snapping straight into
			// combat_idle's near-instant single frame.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getAttackSwingAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getAttackSwingReturnAnimation()));
			ClientPlayNetworking.send(new MovePayload(MovePayload.STAB, aimedEntityId(client)));
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

		if (pickJustPressed && drawn && !interactingWithBlock(client)) {
			// Drawn, the wakizashi sits in the offhand, so this is the mirrored
			// left-arm animation. thenPlayAndHold, unlike the undrawn cut: drawn there
			// IS a stance to settle back into.
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getWakizashiCutOffhandAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getWakizashiCutOffhandReturnAnimation()));

			ClientPlayNetworking.send(new MovePayload(MovePayload.CUT, aimedEntityId(client)));
		}

		boolean useDown = client.options.keyUse.isDown();
		boolean useJustPressed = useDown && !lastUseDown;
		lastUseDown = useDown;

		// Right-clicking a FUNCTIONAL block is an interaction with that block, not a
		// slice - the katana's move IS right-click, so opening a chest or a furnace
		// fired the animation and the move alongside the GUI.
		//
		// Only blocks that answer a click - see interactingWithBlock. Facing a wall
		// still swings; opening a chest does not.
		if (interactingWithBlock(client)) {
			useJustPressed = false;
		}

		// Same slice, same animation whether it's landing as the paired
		// finisher after a wakizashi stab or on its own with no stab in
		// progress - the katana works standalone too (the functional
		// server-side difference between the two is handled entirely by
		// SwordComboHandler, not here).
		if (useJustPressed && drawn) {
			PlayerHandAnimator.trigger(player, RawAnimation.begin()
					.thenPlay(TwoHeavensPlayerAnimation.getKatanaSliceAnimation())
					.thenPlayAndHold(TwoHeavensPlayerAnimation.getKatanaSliceReturnAnimation()));
			ClientPlayNetworking.send(new MovePayload(MovePayload.KATANA, aimedEntityId(client)));
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
			ClientPlayNetworking.send(new MovePayload(MovePayload.KATANA, aimedEntityId(client)));
		}
	}
}
