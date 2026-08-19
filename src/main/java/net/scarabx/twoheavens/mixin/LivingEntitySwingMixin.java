package net.scarabx.twoheavens.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
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
		SweepEffect.playFromSwing(player);
	}
}
