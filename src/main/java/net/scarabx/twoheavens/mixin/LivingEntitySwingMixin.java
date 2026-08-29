package net.scarabx.twoheavens.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import net.scarabx.twoheavens.combat.InteractableBlocks;
import net.scarabx.twoheavens.combat.SwordBlockGuard;
import net.scarabx.twoheavens.combat.SweepEffect;
import net.scarabx.twoheavens.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Katana/wakizashi sweep sound + particles used to only ever fire from
 * hurtEnemy (KatanaItem/WakizashiItem), which requires an actual landed hit
 * on a LivingEntity - there's no target to call it on for a plain swing at
 * air. This mixin catches every genuinely-new swing (LivingEntity#swing
 * resets swingTime to -1 exactly when a new swing actually starts, as
 * opposed to a redundant call mid-swing) so the effect plays outside
 * combat/draw mode even without hitting anything. SweepEffect dedupes
 * against the same-tick hit-triggered version so a landed hit doesn't play
 * it twice.
 */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {

	/**
	 * Cancels the swing OUTRIGHT when a blade is used on a block that answers a click.
	 *
	 * Vanilla calls player.swing(hand) on any successful block use, so opening a chest
	 * with a katana played the generic arm swing on top of everything else we had
	 * already suppressed. Blocking it here rather than at the tail means the arm does
	 * not move at all: the click belongs to the block, and the sword should look
	 * uninvolved.
	 *
	 * HEAD and cancellable, unlike the sweep hook below - by TAIL the swing has already
	 * been started and broadcast. Runs on both sides because swing() does: the client
	 * for the animation, the server for other players seeing it.
	 */
	@Inject(at = @At("HEAD"), method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", cancellable = true)
	private void twoheavens$blockUseSwing(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo info) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Player player)) {
			return;
		}
		Item held = player.getItemInHand(InteractionHand.MAIN_HAND).getItem();
		if (held != ModItems.KATANA && held != ModItems.WAKIZASHI) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(5.0));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end,
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK
				&& InteractableBlocks.answersClick(player.level(), hit.getBlockPos())) {
			info.cancel();
		}
	}

	@Inject(at = @At("TAIL"), method = "swing(Lnet/minecraft/world/InteractionHand;Z)V")
	private void twoheavens$swingSweep(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo info) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide() || hand != InteractionHand.MAIN_HAND || self.swingTime != -1) {
			return;
		}
		if (!(self instanceof ServerPlayer player) || player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			return;
		}
		Item item = player.getItemInHand(hand).getItem();
		if (item != ModItems.KATANA && item != ModItems.WAKIZASHI) {
			return;
		}
		// An undrawn katana's left-click is cancelled outright (SwordComboHandler),
		// so it must not announce itself with a sweep either - that read as the swing
		// having done something when it had not.
		if (item == ModItems.KATANA) {
			return;
		}
		// A swing that landed on a block is not an attack.
		//
		// The important case is not left-click at all: vanilla swings the arm on a
		// SUCCESSFUL BLOCK USE - useItemOn calls player.swing(hand) - so opening a chest
		// or a furnace arrived here and played the wakizashi's sweep. The katana was
		// unaffected only because it returns early above, which is why one sword went
		// quiet and the other did not.
		//
		// Raycast rather than a recorded flag: the flag came from AttackBlockCallback,
		// which is vanilla's LEFT-click path and never fires for a right-click use.
		if (SwordBlockGuard.consumeBlockSwing(player) || aimingAtBlock(player)) {
			return;
		}
		SweepEffect.playFromSwing(player);
	}

	/** Only blocks that answer a click - a block entity is the closest available signal. */
	private static boolean aimingAtBlock(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(5.0));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end,
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK
				&& InteractableBlocks.answersClick(player.level(), hit.getBlockPos());
	}
}
