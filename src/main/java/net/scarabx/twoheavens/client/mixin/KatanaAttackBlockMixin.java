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
 * Stops the undrawn katana's left-click dead, swing included.
 *
 * Cancelling AttackEntityCallback is not enough on its own: Minecraft#startAttack
 * ends with an unconditional player.swing(MAIN_HAND) after its hit-type switch, so
 * the arm moves whether the attack was cancelled, whether a block was hit, or
 * whether the click landed on nothing at all. The only veto that runs early enough
 * is cannotAttackWithItem - startAttack returns immediately on true, before the
 * entity attack, the block hit and the swing.
 *
 * Client-side only by nature (startAttack is its sole caller). SwordComboHandler
 * keeps its own server-side FAIL so a client that ignores this still cannot land
 * the hit.
 */
@Mixin(Player.class)
public class KatanaAttackBlockMixin {

	@Inject(at = @At("HEAD"), method = "cannotAttackWithItem", cancellable = true)
	private void twoheavens$blockUndrawnKatanaAttack(ItemStack itemStack, int tolerance,
			CallbackInfoReturnable<Boolean> info) {
		Player self = (Player) (Object) this;
		if (itemStack.getItem() == ModItems.KATANA && !self.hasAttached(DrawnSwordsAttachment.TYPE)) {
			info.setReturnValue(true);
		}
	}
}
