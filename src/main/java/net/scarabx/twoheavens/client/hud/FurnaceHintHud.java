package net.scarabx.twoheavens.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import net.scarabx.twoheavens.TwoHeavens;
import net.minecraft.world.level.block.state.BlockState;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.block.custom.TataraFurnaceBlock;
import net.scarabx.twoheavens.block.custom.TataraFurnaceFiredBlock;
import net.scarabx.twoheavens.item.ModItems;

/**
 * A prompt shown while an unfired Tatara Furnace is in view: right-click it with
 * eight charcoal, then wait a minute. Nothing on the block itself suggests either,
 * and both are easy to get wrong once and give up on.
 *
 * Looking at the block roughly - anywhere in the forward cone - rather than
 * precisely under the crosshair, since the furnace is a big object you naturally
 * stand back from.
 */
public final class FurnaceHintHud {

	// A GUI atlas sprite id, not a file path: blitSprite resolves this to
	// assets/twoheavens/textures/gui/sprites/mouse_right_click.png
	private static final Identifier MOUSE_ICON = TwoHeavens.id("mouse_right_click");
	private static final int MOUSE_W = 12;
	private static final int MOUSE_H = 16;

	/** How far away the furnace can be and still prompt. */
	private static final double RANGE = 8.0;
	/** cos of the half-angle counted as "looking at it" - about 40 degrees off-centre. */
	private static final double VIEW_DOT = 0.76;

	private static final int UNFIRED_CHARCOAL = 8;
	private static final int FIRED_CHARCOAL = 4;
	private static final int FIRED_SATETSU = 4;
	/** redness_stage once heat reaches the passive cap - i.e. the bellows half has begun. */
	private static final int BELLOWS_PHASE_STAGE = 4;
	private static final int ICON = 16;
	private static final int GAP = 6;
	/**
	 * Height above the bottom edge. Kept well clear of the status bars, which stack
	 * upward as the player gains armour and air, rather than hugging the hotbar.
	 */
	private static final int BOTTOM_OFFSET = 96;

	private FurnaceHintHud() {
	}

	public static void register() {
		HudElementRegistry.addLast(TwoHeavens.id("furnace_hint"), FurnaceHintHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}

		BlockState unfired = furnaceInView(client, ModBlocks.TATARA_FURNACE);
		if (unfired != null) {
			if (unfired.getValue(TataraFurnaceBlock.LIT)) {
				return;
			}
			int missing = UNFIRED_CHARCOAL - unfired.getValue(TataraFurnaceBlock.CHARCOAL_LEVEL);
			if (missing > 0) {
				drawHint(graphics, client,
						List.of(new Ingredient(new ItemStack(Items.CHARCOAL, missing))),
						Component.translatable("hud.twoheavens.furnace_wait"));
			} else {
				drawLightHint(graphics, client);
			}
			return;
		}

		BlockState fired = furnaceInView(client, ModBlocks.TATARA_FURNACE_FIRED);
		if (fired == null || fired.getValue(TataraFurnaceFiredBlock.KERA_FORMED)) {
			return;
		}

		if (!fired.getValue(TataraFurnaceFiredBlock.LIT)) {
			int charcoal = FIRED_CHARCOAL - fired.getValue(TataraFurnaceFiredBlock.CHARCOAL_LEVEL);
			int satetsu = FIRED_SATETSU - fired.getValue(TataraFurnaceFiredBlock.SATETSU_LEVEL);

			// Only list what is still missing; once both are full the block is ready
			// to light and the prompt becomes the flint and steel instead.
			List<Ingredient> missing = new ArrayList<>(2);
			if (charcoal > 0) {
				missing.add(new Ingredient(new ItemStack(Items.CHARCOAL, charcoal)));
			}
			if (satetsu > 0) {
				missing.add(new Ingredient(new ItemStack(ModBlocks.SATETSU_SAND, satetsu)));
			}

			if (missing.isEmpty()) {
				drawLightHint(graphics, client);
			} else {
				drawHint(graphics, client, missing,
						Component.translatable("hud.twoheavens.furnace_wait_short"));
			}
		} else if (fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE) >= BELLOWS_PHASE_STAGE) {
			// Past the passive half - heat now falls without bellows work.
			drawHint(graphics, client,
					List.of(new Ingredient(new ItemStack(ModItems.BELLOWS))),
					Component.translatable("hud.twoheavens.furnace_bellows"));
		}
		// Lit but still in the passive half: nothing to do yet, so stay quiet.
	}

	/** Filled and ready - the only remaining step is striking it alight. */
	private static void drawLightHint(GuiGraphicsExtractor graphics, Minecraft client) {
		drawHint(graphics, client,
				List.of(new Ingredient(new ItemStack(Items.FLINT_AND_STEEL))),
				Component.empty());
	}

	/** The stack's own count is the amount; itemDecorations draws it corner-style. */
	private record Ingredient(ItemStack stack) {
	}

	private static void drawHint(GuiGraphicsExtractor graphics, Minecraft client,
								  List<Ingredient> ingredients, Component tail) {
		Font font = client.font;

		Component plus = Component.literal("+");
		int plusWidth = font.width(plus);

		int width = MOUSE_W;
		for (Ingredient ingredient : ingredients) {
			width += GAP + plusWidth + GAP + ICON;
		}
		boolean hasTail = !tail.getString().isEmpty();
		if (hasTail) {
			width += GAP + plusWidth + GAP + font.width(tail);
		}

		// Centred above the hotbar, where vanilla shows the held item name - somewhere
		// players already scan, and out of the crosshair's way.
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() - BOTTOM_OFFSET;
		int textY = y + (MOUSE_H - font.lineHeight) / 2;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, MOUSE_ICON, x, y, MOUSE_W, MOUSE_H);
		int cursor = x + MOUSE_W;

		for (Ingredient ingredient : ingredients) {
			// A plus between every step, including after the mouse, so the whole
			// prompt reads as one instruction rather than loose icons.
			cursor += GAP;
			graphics.text(font, plus, cursor, textY, 0xFFFFFFFF);
			cursor += plusWidth + GAP;

			graphics.item(ingredient.stack(), cursor, y);
			graphics.itemDecorations(font, ingredient.stack(), cursor, y);
			cursor += ICON;
		}

		if (hasTail) {
			cursor += GAP;
			graphics.text(font, plus, cursor, textY, 0xFFFFFFFF);
			cursor += plusWidth + GAP;
			graphics.text(font, tail, cursor, textY, 0xFFFFFFFF);
		}
	}



	/** True when any furnace of the given kind within RANGE sits inside the player's forward cone. */
	private static BlockState furnaceInView(Minecraft client, net.minecraft.world.level.block.Block block) {
		Level level = client.level;
		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getLookAngle().normalize();
		BlockPos origin = client.player.blockPosition();
		int r = (int) Math.ceil(RANGE);

		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(block)) {
				continue;
			}
			Vec3 toBlock = Vec3.atCenterOf(pos).subtract(eye);
			if (toBlock.length() > RANGE) {
				continue;
			}
			if (look.dot(toBlock.normalize()) >= VIEW_DOT) {
				return state;
			}
		}
		return null;
	}
}
