package net.scarabx.twoheavens.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

	public record StoredItems(ItemStack mainHand, ItemStack offHand) {
		public static final Codec<StoredItems> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ItemStack.OPTIONAL_CODEC.fieldOf("main_hand").forGetter(StoredItems::mainHand),
				ItemStack.OPTIONAL_CODEC.fieldOf("off_hand").forGetter(StoredItems::offHand)
		).apply(instance, StoredItems::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, StoredItems> STREAM_CODEC = StreamCodec.composite(
				ItemStack.OPTIONAL_STREAM_CODEC, StoredItems::mainHand,
				ItemStack.OPTIONAL_STREAM_CODEC, StoredItems::offHand,
				StoredItems::new);
	}
}
