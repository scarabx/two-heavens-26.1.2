package net.scarabx.twoheavens.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.ModItems;

/**
 * Two starter hints shown on join, each dropping away once its goal is met.
 * The item pictures are glyphs from the twoheavens:icons font
 * (assets/twoheavens/font/icons.json) - chat can't embed textures any other way.
 */
public final class JoinMessageHandler {

	private static final int SATETSU_GOAL = 8;
	private static final int TATARA_CLAY_GOAL = 64;
	/** One Sugar Cane + Charcoal + Clay Ball yields this many Tatara Clay. */
	private static final int TATARA_CLAY_PER_CRAFT = 4;

	// withoutShadow keeps chat's drop shadow off the item pictures - on a glyph it
	// smears a dark copy under the sprite rather than reading as depth.
	private static final Style ICON_STYLE = Style.EMPTY
			.withFont(new FontDescription.Resource(TwoHeavens.id("icons")))
			.withoutShadow();

	// Written as unicode escapes on purpose - literal private-use characters get
	// stripped by some editors and shell tooling, silently blanking every icon.
	// (Java expands those escapes even inside comments, so don't spell one out here.)
	private static final String SATETSU = "\uE000";
	private static final String SUGAR_CANE = "\uE001";
	private static final String CHARCOAL = "\uE002";
	private static final String CLAY = "\uE003";
	private static final String TATARA_CLAY = "\uE004";

	private JoinMessageHandler() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			TutorialProgressAttachment.Progress progress = TutorialProgressAttachment.sample(player);

			if (progress.maxSatetsu() < SATETSU_GOAL) {
				player.sendSystemMessage(satetsuLine(SATETSU_GOAL - progress.maxSatetsu()));
			}
			if (needsTataraClayHint(progress)) {
				player.sendSystemMessage(tataraClayLine(progress));
			}
		});

	}

	private static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(ICON_STYLE);
	}

	private static MutableComponent satetsuLine(int remaining) {
		return Component.literal("Mine " + remaining + " ").withStyle(ChatFormatting.GRAY)
				.append(icon(SATETSU))
				.append(Component.literal(" Satetsu from sand next to any water").withStyle(ChatFormatting.GRAY));
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

		return Component.literal(cane + " ").withStyle(ChatFormatting.GRAY)
				.append(icon(SUGAR_CANE))
				.append(Component.literal(" + " + charcoal + " ").withStyle(ChatFormatting.GRAY))
				.append(icon(CHARCOAL))
				.append(Component.literal(" + " + clay + " ").withStyle(ChatFormatting.GRAY))
				.append(icon(CLAY))
				.append(Component.literal(" = " + remaining + " ").withStyle(ChatFormatting.GRAY))
				.append(icon(TATARA_CLAY))
				.append(Component.literal(" Tatara Clay").withStyle(ChatFormatting.GRAY));
	}
}
