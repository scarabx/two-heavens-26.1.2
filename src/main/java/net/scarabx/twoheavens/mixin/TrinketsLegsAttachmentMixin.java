package net.scarabx.twoheavens.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the belt slot attached to the leggings slot, and therefore hover-revealed,
 * whatever the player's Trinkets sidebar setting says.
 *
 * Trinkets has two layouts, chosen by one global client flag: attached (a group
 * hangs off its equipment slot and appears on hover) and sidebar (every group is
 * forced into an always-visible column starting at the top, next to the helmet).
 * That flag is why the same jar looked right on one machine and wrong on another.
 *
 * The upstream method is:
 *
 *     hasSlotAttachment() = !TrinketsConfig.instance.sidebarTrinketsSlots
 *                           && this.slotId != -1
 *
 * It is a PER-GROUP instance method, which is the only reason this is worth doing:
 * forcing the return value for "legs" alone leaves every other group - and so every
 * other trinket mod's slots - on whatever the player chose. Defeating the flag
 * itself would relayout their whole inventory from a mod with no business doing it.
 *
 * The `slotId != -1` half is preserved deliberately: -1 means the group has no
 * equipment slot to attach to, and forcing attachment there would place it nowhere.
 *
 * FAILS SOFT, and that is the point of every annotation here. `SlotGroupImpl` is
 * `impl`, not API, so Trinkets is free to rename or delete it in any release -
 * including the beta line this mod depends on. @Pseudo lets the mixin be skipped
 * when the target class is absent instead of throwing at startup, and require = 0
 * does the same when the method is gone. The cost of being wrong is then a belt
 * slot in the sidebar, which is survivable; a hard mixin failure is a crash on
 * launch, which is not.
 *
 * If this stops working after a Trinkets update, DELETE IT rather than chasing the
 * rename. The tooltip is written to be true in both layouts, so the mod is correct
 * without this file - it is a preference, not a requirement.
 */
@Pseudo
@Mixin(targets = "eu.pb4.trinkets.impl.SlotGroupImpl", remap = false)
public class TrinketsLegsAttachmentMixin {

	@Shadow
	@Final
	private String name;

	@Shadow
	@Final
	private int slotId;

	@Inject(method = "hasSlotAttachment", at = @At("HEAD"), cancellable = true, require = 0)
	private void twoheavens$keepBeltOnTheLeggings(CallbackInfoReturnable<Boolean> cir) {
		if ("legs".equals(this.name) && this.slotId != -1) {
			cir.setReturnValue(true);
		}
	}
}
