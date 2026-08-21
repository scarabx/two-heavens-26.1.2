package net.scarabx.twoheavens.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Presence of this attachment on a player IS the "swords drawn" state - a
 * plain server-memory Map (the original approach) doesn't survive a full
 * client/JVM restart, even though the actual held items do (via normal world
 * save/load), leaving the two permanently out of sync after any relaunch.
 * Attaching this directly to the player entity ties it to the SAME
 * persistence/sync mechanism the game already uses for the entity itself -
 * .persistent(...) saves it with the player's own NBT, .syncWith(...) pushes
 * it to the owning client automatically on every change and on join, so no
 * custom packet or resend-on-a-timer logic is needed at all.
 */
public class DrawnSwordsAttachment {

	public static final AttachmentType<StoredItems> TYPE = AttachmentRegistry.<StoredItems>builder()
			.persistent(StoredItems.CODEC)
			.syncWith(StoredItems.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(TwoHeavens.id("drawn_swords"));

	// No-op call site whose only purpose is forcing this class to load (and
	// TYPE to actually register) during mod init - referencing TYPE lazily
	// from inside an event lambda that only fires mid-game is too late.
	public static void touch() {
	}

	// mainHandSlot is the hotbar slot the real main-hand item was pulled
	// from at draw time - sheathe writes it back to that exact slot instead
	// of "whatever the currently selected slot happens to be". Without this,
	// any drift between the selected slot at draw-time and at sheathe-time
	// (e.g. a stray Inventory#setSelectedSlot call slipping past the
	// hotbar-lock mixins) would drop the real item into the wrong slot while
	// leaving the original slot - and the stray real katana/wakizashi
	// already sitting there from a previous draw cycle - untouched, so the
	// next draw looked like it conjured a THIRD sword.
	public record StoredItems(int mainHandSlot, ItemStack mainHand, ItemStack offHand) {
		public static final Codec<StoredItems> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("main_hand_slot").forGetter(StoredItems::mainHandSlot),
				ItemStack.OPTIONAL_CODEC.fieldOf("main_hand").forGetter(StoredItems::mainHand),
				ItemStack.OPTIONAL_CODEC.fieldOf("off_hand").forGetter(StoredItems::offHand)
		).apply(instance, StoredItems::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, StoredItems> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT.cast(), StoredItems::mainHandSlot,
				ItemStack.OPTIONAL_STREAM_CODEC, StoredItems::mainHand,
				ItemStack.OPTIONAL_STREAM_CODEC, StoredItems::offHand,
				StoredItems::new);
	}
}
