package net.scarabx.twoheavens.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Two starter hints shown once per join, pointing at the first two steps of the
 * smithing chain. The item pictures are glyphs from the twoheavens:icons font
 * (assets/twoheavens/font/icons.json) - chat can't embed textures any other way.
 */
public final class JoinMessageHandler {

	// withoutShadow keeps chat's drop shadow off the item pictures - on a glyph it
	// smears a dark copy under the sprite rather than reading as depth.
	private static final Style ICON_STYLE = Style.EMPTY
			.withFont(new FontDescription.Resource(TwoHeavens.id("icons")))
			.withoutShadow();

	private static final String SATETSU = "";
	private static final String SUGAR_CANE = "";
	private static final String CHARCOAL = "";
	private static final String CLAY = "";
	private static final String TATARA_CLAY = "";

	private JoinMessageHandler() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			handler.getPlayer().sendSystemMessage(firstLine());
			handler.getPlayer().sendSystemMessage(secondLine());
		});
	}

	private static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(ICON_STYLE);
	}

	private static MutableComponent firstLine() {
		return Component.literal("Mine 16 Satetsu ").withStyle(ChatFormatting.GRAY)
				.append(icon(SATETSU))
				.append(Component.literal(" from sand next to any body of water.").withStyle(ChatFormatting.GRAY));
	}

	private static MutableComponent secondLine() {
		return Component.literal("Craft 16 Sugar Cane ").withStyle(ChatFormatting.GRAY)
				.append(icon(SUGAR_CANE))
				.append(Component.literal(", 16 Charcoal ").withStyle(ChatFormatting.GRAY))
				.append(icon(CHARCOAL))
				.append(Component.literal(" and 16 Clay ").withStyle(ChatFormatting.GRAY))
				.append(icon(CLAY))
				.append(Component.literal(" into 64 Tatara Clay ").withStyle(ChatFormatting.GRAY))
				.append(icon(TATARA_CLAY))
				.append(Component.literal(".").withStyle(ChatFormatting.GRAY));
	}
}
