package net.scarabx.twoheavens.client.mixin;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.scarabx.twoheavens.ModMessages;
import net.scarabx.twoheavens.client.DrawnRefusalToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repeats this mod's chat messages as a toast when a screen is covering the chat.
 *
 * **Chat is not drawn behind an open screen**, and several of this mod's messages fire
 * from exactly there. The worst is the obi handoff - "Katana crafted", then "Craft both
 * swords into a Daisho Saya" - which fires as the player takes their first sword out of
 * a CRAFTING TABLE. That is the pointer to the entire combat half of the mod, and it has
 * been landing behind the screen it was triggered by. The goal crossings have the same
 * problem when a chest is what filled the counter, and the drawn-hands refusal fires
 * while the inventory is open by definition.
 *
 * General rather than per-message on purpose: the trigger is "a screen is open", which
 * is a property of the moment, not of any particular line. Special-casing one message
 * would leave the others silently broken - which is how this was missed in the first
 * place.
 *
 * Matched by the marker `ModMessages` puts on every line this mod sends, so it only
 * ever repeats OUR messages. A translation-key prefix was the first attempt and was
 * wrong: most of these are `Component.literal`, so the most important one - the obi
 * handoff, which fires as the player takes their first sword out of a crafting table -
 * would have been missed.
 */
@Mixin(ChatComponent.class)
public class ChatToastMixin {

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
	private void twoheavens$toastWhenCovered(GuiMessage message, CallbackInfo ci) {
		if (!DrawnRefusalToast.screenIsOpen()) {
			return;
		}
		Component content = message.content();
		if (ModMessages.isOurs(content)) {
			DrawnRefusalToast.show(content);
		}
	}
}
