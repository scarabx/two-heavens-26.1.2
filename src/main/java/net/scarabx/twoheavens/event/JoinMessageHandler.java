package net.scarabx.twoheavens.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.ModItems;

/**
 * Two starter hints shown on join, each dropping away once its goal is met.
 * The item pictures are glyphs from the twoheavens:icons font
 * (assets/twoheavens/font/icons.json) - chat can't embed textures any other way.
 *
 * Counts are written inline before each icon rather than in the corner, the way an
 * inventory slot shows them. Chat cannot overlay one on the other: it lays glyphs
 * out left to right with no way back, so a corner number would need a negative-space
 * font plus a custom small-digit font. Not worth it for two lines.
 *
 * Text is left unstyled, which renders white - grey read as disabled against chat's
 * background.
 */
public final class JoinMessageHandler {

	private static final int SATETSU_GOAL = 8;
	private static final int TATARA_CLAY_GOAL = 64;
	/** One Sugar Cane + Charcoal + Clay Ball yields this many Tatara Clay. */
	private static final int TATARA_CLAY_PER_CRAFT = 4;
	/** How many of each ingredient the whole clay goal needs. */
	private static final int INGREDIENTS_PER_GOAL = TATARA_CLAY_GOAL / TATARA_CLAY_PER_CRAFT;

	// withoutShadow keeps chat's drop shadow off the item pictures - on a glyph it
	// smears a dark copy under the sprite rather than reading as depth.
	private static final Style ICON_STYLE = Style.EMPTY
			.withFont(new FontDescription.Resource(TwoHeavens.id("icons")))
			.withColor(ChatFormatting.WHITE)
			.withoutShadow();

	// Written as unicode escapes on purpose - literal private-use characters get
	// stripped by some editors and shell tooling, silently blanking every icon.
	// (Java expands those escapes even inside comments, so don't spell one out here.)
	private static final String SATETSU = "\uE000";
	private static final String SUGAR_CANE = "\uE001";
	private static final String CHARCOAL = "\uE002";
	private static final String CLAY = "\uE003";
	private static final String TATARA_CLAY = "\uE004";
	/** Shared with AnvilForgingHandler, which names the Tongs in an overlay message. */
	static final String TONGS = "\uE005";

	private JoinMessageHandler() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			TutorialProgressAttachment.Progress progress = TutorialProgressAttachment.sample(player);

			if (progress.maxSatetsu() < SATETSU_GOAL) {
				net.scarabx.twoheavens.ModMessages.send(player, satetsuLine(SATETSU_GOAL - progress.maxSatetsu()));
			}
			if (needsTataraClayHint(progress)) {
				net.scarabx.twoheavens.ModMessages.send(player, tataraClayLine(progress));
			}
		});

	}


	/**
	 * Chat lines confirming a starter goal is done, sent the moment it is reached
	 * rather than waiting for the next join.
	 *
	 * Without these the hints simply stop appearing on some future login, which reads
	 * as them having been forgotten rather than completed - and a player who never
	 * leaves the world would never see them go at all.
	 *
	 * Fired on the crossing only: compares what was stored against what was just
	 * counted, so it cannot repeat however many times sample() runs.
	 */
	static void announceCompletions(ServerPlayer player,
									 TutorialProgressAttachment.Progress before,
									 TutorialProgressAttachment.Progress after) {
		if (before.maxSatetsu() < SATETSU_GOAL && after.maxSatetsu() >= SATETSU_GOAL) {
			net.scarabx.twoheavens.ModMessages.send(player, Component.literal(SATETSU_GOAL + " ")
					.append(icon(SATETSU))
					.append(Component.literal(" mined")));
			// Sent as its own line rather than appended to the one above. The first is a
			// confirmation - past tense, closes a loop; this is an instruction. Running
			// them together reads as neither, and that line already carries a glyph.
			//
			// Satetsu is not crafted into anything, so it gets no recipe pointer: it is
			// spent much later, filling the fired furnace, and aiming the player at the
			// crafting grid here would send them looking for something that isn't there.
			//
			// Names the Tatara Furnace rather than "the smelt", which read as vanilla
			// smelting - a wrong steer at the moment the player is casting about for
			// something to do. Deliberately the SAME phrase the clay message uses, so the
			// two teach one name between them whichever goal completes first. Not "kera"
			// (the output of a process they have not met) and not "Fired Tatara Furnace"
			// (a distinction that does not exist for them yet).
			net.scarabx.twoheavens.ModMessages.send(player, Component.literal("Set it aside for the Tatara Furnace"));
			ping(player);
		}

		// The last stretch, and the only one nothing announced. A finished katana is
		// where the chain hands off from smithing to wearing it: the tooltip already
		// names the Daisho Obi, but only once you are holding the sword, and nothing
		// ever said the obi and saya exist or that they are what you carry blades on.
		// Fires on whichever sword is finished FIRST, and never again for the second -
		// the handoff is about the pair, so saying it twice would be telling someone
		// something they already acted on.
		boolean hadBlade = before.maxKatana() > 0 || before.maxWakizashi() > 0;
		boolean hasBlade = after.maxKatana() > 0 || after.maxWakizashi() > 0;
		if (!hadBlade && hasBlade) {
			net.scarabx.twoheavens.ModMessages.send(player, Component.literal(
					after.maxKatana() > 0 ? "Katana crafted" : "Wakizashi crafted"));
			net.scarabx.twoheavens.ModMessages.send(player, Component.literal(
					"Craft both swords into a Daisho Saya, and then into an Obi to dual wield"));
			ping(player);
		}

		int per = INGREDIENTS_PER_GOAL;
		boolean had = before.maxSugarCane() >= per && before.maxCharcoal() >= per && before.maxClay() >= per;
		boolean has = after.maxSugarCane() >= per && after.maxCharcoal() >= per && after.maxClay() >= per;
		if (!had && has) {
			net.scarabx.twoheavens.ModMessages.send(player, Component.literal(per + " ")
					.append(icon(SUGAR_CANE))
					.append(Component.literal(" + " + per + " "))
					.append(icon(CHARCOAL))
					.append(Component.literal(" + " + per + " "))
					.append(icon(CLAY))
					.append(Component.literal(" gathered")));

			// The seam the guidance used to fall through. Join hints cover gathering and
			// retire the moment it is done; the furnace HUD needs a furnace in view to
			// fire at all - so finishing the clay silenced both layers at once, leaving
			// the player holding everything they need and nothing telling them what it
			// was for. This is the exact instant they ask "now what", so it is answered
			// here rather than left to a permanent line that would have to be skimmed
			// past on every future join.
			net.scarabx.twoheavens.ModMessages.send(player, Component.literal("Craft Tatara Clay, then a Tatara Furnace"));

			// No recipe pointer here on purpose. ItemTooltipMixin already prints "Hold
			// [Shift] for recipes" on the vanilla ingredients themselves, so a chat line
			// saying the same thing can only reach a player who is already hovering an
			// item - and that player has been told. What was actually missing is a
			// REASON to hover: the line above supplies it, since Tatara Clay cannot be
			// guessed from vanilla knowledge and crafting it means opening the inventory,
			// which is the only place a tooltip exists.
			ping(player);
		}
	}

	/**
	 * The chime that marks a pointer landing in chat.
	 *
	 * The rule: a mod chat message gets this if it appeared BECAUSE something just
	 * happened. That is the goal crossings here and the furnace's tend alert, which
	 * is the one that needs it most - it fires while the player is looking at the
	 * world, not at the chat box.
	 *
	 * The join hints are the deliberate exception. They repeat every login and say
	 * what is still outstanding rather than what changed, so pinging them would
	 * teach the sound to mean "nothing new" within three logins and make it noise
	 * everywhere else. The sound means read chat now; a join hint is the one case
	 * where there is nothing new to read.
	 *
	 * Once per BURST, not per line - each of these announcements is a confirmation
	 * and the instruction that follows it, and the two are one event.
	 *
	 * Played at the player's own position with a null exclusion, which is the same
	 * shape every other sound in the mod uses. NOT player.playSound(...), which is
	 * Player#playSound and excludes the player it is called on - the one listener
	 * this sound exists for. Volume 0.6 and pitch 1.4 lift it off the orb pickup the
	 * sample is otherwise heard as, which plays constantly and would let a pointer
	 * pass for ordinary XP.
	 */
	public static void ping(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(ICON_STYLE);
	}

	private static MutableComponent satetsuLine(int remaining) {
		return Component.literal("Mine " + remaining + " ")
				.append(icon(SATETSU))
				.append(Component.literal(" Satetsu from sand next to any water"));
	}

	/**
	 * The line tells you what to go and gather, so it retires once there is nothing
	 * left to gather - either because the clay is already made, or because all three
	 * ingredients are in hand. Without the second check it would sit there reading
	 * "0 + 0 + 0", asking for nothing.
	 */
	private static boolean needsTataraClayHint(TutorialProgressAttachment.Progress progress) {
		if (progress.maxTataraClay() >= TATARA_CLAY_GOAL) {
			return false;
		}
		int required = requiredPerIngredient(progress);
		return progress.maxSugarCane() < required
				|| progress.maxCharcoal() < required
				|| progress.maxClay() < required;
	}

	private static int requiredPerIngredient(TutorialProgressAttachment.Progress progress) {
		int remaining = TATARA_CLAY_GOAL - progress.maxTataraClay();
		return (remaining + TATARA_CLAY_PER_CRAFT - 1) / TATARA_CLAY_PER_CRAFT;
	}

	private static MutableComponent tataraClayLine(TutorialProgressAttachment.Progress progress) {
		int remaining = TATARA_CLAY_GOAL - progress.maxTataraClay();
		int perIngredient = requiredPerIngredient(progress);

		// Each ingredient counts down against what the player has already gathered,
		// the same way the satetsu line does - so the numbers shrink as you collect.
		int cane = Math.max(0, perIngredient - progress.maxSugarCane());
		int charcoal = Math.max(0, perIngredient - progress.maxCharcoal());
		int clay = Math.max(0, perIngredient - progress.maxClay());

		return Component.literal(cane + " ")
				.append(icon(SUGAR_CANE))
				.append(Component.literal(" + " + charcoal + " "))
				.append(icon(CHARCOAL))
				.append(Component.literal(" + " + clay + " "))
				.append(icon(CLAY))
				.append(Component.literal(" = " + remaining + " "))
				.append(icon(TATARA_CLAY))
				.append(Component.literal(" Tatara Clay"));
	}
}
