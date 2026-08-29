package net.scarabx.twoheavens.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.item.ModItems;

import java.util.function.Supplier;

/**
 * High-water marks for the two starter goals, saved with the player's own NBT.
 *
 * Deliberately NOT a live inventory count: the moment you tip your satetsu into
 * the furnace your count drops to zero, and a plain count would bring the hint
 * back exactly when you have progressed past needing it. Recording the most you
 * have ever carried means once a goal is reached it stays reached.
 */
public class TutorialProgressAttachment {

	// copyOnDeath is what makes these HIGH-WATER marks rather than just saved ones.
	// persistent() alone survives a server restart but NOT a respawn: dying handed the
	// new player entity a fresh, empty attachment, so re-collecting your own dropped
	// satetsu crossed the goal a second time and announced it again. Every counter
	// here describes what the player has achieved, not what they are carrying, and
	// dying does not undo an achievement.
	public static final AttachmentType<Progress> TYPE = AttachmentRegistry.<Progress>builder()
			.persistent(Progress.CODEC)
			.copyOnDeath()
			.buildAndRegister(TwoHeavens.id("tutorial_progress"));

	// Forces this class to load (and TYPE to register) during mod init rather
	// than lazily from an event lambda - same reasoning as DrawnSwordsAttachment.
	public static void touch() {
	}

	/** The items whose totals the starter hints count down against. */
	private static final Supplier<Item>[] TRACKED = new Supplier[]{
			() -> ModBlocks.SATETSU_SAND.asItem(),
			() -> Items.SUGAR_CANE,
			() -> Items.CHARCOAL,
			() -> Items.CLAY_BALL,
			() -> ModItems.TATARA_CLAY,
			// Either finished sword hands the chain off to the obi and saya - the last
			// stretch, and the only one nothing announced. Both are tracked because
			// either can be made first, and the handoff should fire on whichever it is.
			() -> ModItems.KATANA,
			() -> ModItems.WAKIZASHI
	};

	/**
	 * Folds what the player is carrying into their stored maxima.
	 *
	 * Driven by inventory additions rather than a timer. Because every counter is a
	 * max(), running this twice for one pickup is harmless - which is what lets the
	 * mixin fire bluntly on every add without needing to be exact.
	 */
	public static Progress sample(ServerPlayer player) {
		Progress stored = player.getAttachedOrElse(TYPE, Progress.NONE);

		int[] counts = new int[TRACKED.length];
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.isEmpty()) {
				continue;
			}
			// One pass over the inventory for all five counters, not five passes.
			for (int i = 0; i < TRACKED.length; i++) {
				if (stack.is(TRACKED[i].get())) {
					counts[i] += stack.getCount();
					break;
				}
			}
		}

		Progress raised = stored.raisedTo(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6]);
		if (!raised.equals(stored)) {
			player.setAttached(TYPE, raised);
			// Announce here rather than on join: a player who stays in one world would
			// otherwise never see a goal acknowledged, only notice the hint stop.
			JoinMessageHandler.announceCompletions(player, stored, raised);
		}
		return raised;
	}

	public record Progress(int maxSatetsu, int maxSugarCane, int maxCharcoal, int maxClay, int maxTataraClay,
						   int maxKatana, int maxWakizashi) {
		public static final Progress NONE = new Progress(0, 0, 0, 0, 0, 0, 0);

		// Every field defaults to 0 so an older save, written before the ingredient
		// counters existed, still loads instead of failing the whole attachment.
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("max_satetsu", 0).forGetter(Progress::maxSatetsu),
				Codec.INT.optionalFieldOf("max_sugar_cane", 0).forGetter(Progress::maxSugarCane),
				Codec.INT.optionalFieldOf("max_charcoal", 0).forGetter(Progress::maxCharcoal),
				Codec.INT.optionalFieldOf("max_clay", 0).forGetter(Progress::maxClay),
				Codec.INT.optionalFieldOf("max_tatara_clay", 0).forGetter(Progress::maxTataraClay),
				Codec.INT.optionalFieldOf("max_katana", 0).forGetter(Progress::maxKatana),
				Codec.INT.optionalFieldOf("max_wakizashi", 0).forGetter(Progress::maxWakizashi)
		).apply(instance, Progress::new));

		public Progress raisedTo(int satetsu, int sugarCane, int charcoal, int clay, int tataraClay, int katana, int wakizashi) {
			return new Progress(
					Math.max(this.maxSatetsu, satetsu),
					Math.max(this.maxSugarCane, sugarCane),
					Math.max(this.maxCharcoal, charcoal),
					Math.max(this.maxClay, clay),
					Math.max(this.maxTataraClay, tataraClay),
					Math.max(this.maxKatana, katana),
					Math.max(this.maxWakizashi, wakizashi));
		}
	}
}
