package net.scarabx.twoheavens.client.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.combat.DrawnSwordsAttachment;
import net.scarabx.twoheavens.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops vanilla's left-click dead for an undrawn katana or wakizashi.
 *
 * Minecraft#startAttack ends with an unconditional player.swing(MAIN_HAND) after
 * its hit-type switch, so cancelling the attack alone still leaves the arm
 * swinging. cannotAttackWithItem is the only veto that runs early enough:
 * startAttack returns immediately on true, before the entity attack, the block
 * hit and the swing.
 *
 * The two swords need this for opposite reasons:
 *
 * - Katana: its move is right-click, so left-click should do nothing at all.
 * - Wakizashi: its move IS left-click, but vanilla's swing animation was fighting
 *   ours - spamming the cut intermittently produced vanilla's upward slice
 *   instead. Blocking vanilla's swing leaves the button free for our own, which
 *   AttackSwingController triggers directly off the key press and reports to the
 *   server with WakizashiCutPayload.
 *
 * Client-side by nature (startAttack is the sole caller of cannotAttackWithItem).
 */
@Mixin(Player.class)
public class UndrawnSwordAttackBlockMixin {

	@Inject(at = @At("HEAD"), method = "cannotAttackWithItem", cancellable = true)
	private void twoheavens$blockUndrawnSwordAttack(ItemStack itemStack, int tolerance,
			CallbackInfoReturnable<Boolean> info) {
		Player self = (Player) (Object) this;
		if (self.hasAttached(DrawnSwordsAttachment.TYPE)) {
			return;
		}
		if (itemStack.getItem() == ModItems.KATANA || itemStack.getItem() == ModItems.WAKIZASHI) {
			info.setReturnValue(true);
		}
	}
}
