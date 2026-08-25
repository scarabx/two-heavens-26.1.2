package net.scarabx.twoheavens.client.mixin;

import net.minecraft.client.Minecraft;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses pick-block while the swords are drawn.
 *
 * Middle-click is the wakizashi's cut in that state, so vanilla must not also act
 * on it. In survival pick-block is already inert - it only reselects a hotbar slot,
 * and slot switching is blocked while drawn - but in creative it inserts the picked
 * block into a hotbar slot, which with switching blocked risks overwriting the
 * conjured katana in the selected slot. That stack carries the marker sheathing
 * looks for, so losing it would leave the draw state inconsistent.
 */
@Mixin(Minecraft.class)
public class DrawnPickBlockMixin {

	@Inject(at = @At("HEAD"), method = "pickBlockOrEntity", cancellable = true)
	private void twoheavens$blockPickWhileDrawn(CallbackInfo info) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player != null && client.player.hasAttached(DrawnSwordsAttachment.TYPE)) {
			info.cancel();
		}
	}
}
