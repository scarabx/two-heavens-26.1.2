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
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Item;
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
	/**
	 * Water in the quench prompt. Unlike satetsu there is no water ITEM to draw, so
	 * this is a gui sprite rather than an ItemStack. Hand-made rather than taken from
	 * vanilla - redistributing Mojang textures in a mod jar is not ours to do. A
	 * bucket was tried first and reads as "use a bucket", which is not the mechanic.
	 */
	private static final Identifier WATER_ICON = TwoHeavens.id("water");

	/** Hammer strikes needed to break a formed kera out of the furnace. */
	private static final int KERA_HAMMER_STRIKES = 4;
	private static final int ICON = 16;
	private static final int GAP = 6;
	/** Vertical space between stacked rows of a multi-line prompt. */
	private static final int ROW_GAP = 4;
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

		// Carrying a hot blade beats everything else for urgency: it is cooling, and
		// without tongs it burns. Quenching is the next step and nothing says so.
		if (drawQuenchHint(graphics, client)) {
			return;
		}

		// The anvil comes first: if the player is standing at one mid-forge, that is
		// what they are asking about, not a furnace behind them.
		if (drawAnvilHint(graphics, client)) {
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
		if (fired == null) {
			return;
		}
		if (fired.getValue(TataraFurnaceFiredBlock.KERA_FORMED)) {
			// The kera is ready but sealed in. Four hammer strikes break it out, and
			// nothing about the block says so - this is where the guidance used to
			// stop entirely.
			drawHint(graphics, client,
					List.of(new Ingredient(new ItemStack(ModItems.HAMMER))),
					Component.empty(), KERA_HAMMER_STRIKES);
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

	/**
	 * Holding a hot blade with tongs in the off hand: the only remaining step is to
	 * quench it in water, which nothing in the game hints at. Shown as the blade the
	 * player is actually carrying, so the prompt names their own item rather than a
	 * generic one.
	 *
	 * @return true if a prompt was drawn.
	 */
	private static boolean drawQuenchHint(GuiGraphicsExtractor graphics, Minecraft client) {
		ItemStack held = client.player.getMainHandItem();
		if (!held.is(ModItems.HOT_KATANA_BLADE) && !held.is(ModItems.HOT_WAKIZASHI_BLADE)) {
			return false;
		}
		if (!client.player.getOffhandItem().is(ModItems.TONGS)) {
			// No tongs means the blade is about to burn them and eject - a different
			// problem, and the tongs prompt belongs to the anvil step.
			return false;
		}

		drawHint(graphics, client,
				List.of(new Ingredient(new ItemStack(held.getItem())),
						new Ingredient(WATER_ICON)),
				Component.empty());
		return true;
	}

	/**
	 * The anvil sequence, which is where a first-time player was previously left with
	 * no guidance at all: four hammer strikes turn a kera into an ingot, a flat ingot,
	 * a wakizashi blade and finally a katana blade, and nothing says so.
	 *
	 * Reads the stage off the displayed item rather than the display's hit tags - the
	 * item IS the stage, and it needs no access to the server-side handler's internals.
	 *
	 * @return true if a prompt was drawn, so the furnace checks can be skipped.
	 */
	private static boolean drawAnvilHint(GuiGraphicsExtractor graphics, Minecraft client) {
		ItemStack forging = forgingInView(client);
		if (forging.isEmpty()) {
			return false;
		}

		Item item = forging.getItem();
		if (item == ModItems.HOT_KATANA_BLADE) {
			// Finished. Only the tongs remain, and picking it up bare-handed burns.
			drawHint(graphics, client, List.of(new Ingredient(new ItemStack(ModItems.TONGS))),
					Component.translatable("hud.twoheavens.tongs_offhand"), 0, false);
			return true;
		}
		if (item == ModItems.HOT_WAKIZASHI_BLADE) {
			// A fork rather than a step. Two complete rows, each ending in what it
			// produces, so the choice is visible - two icons on one line would not say
			// which did what, and a sentence made the player read mid-fight.
			drawRows(graphics, client, List.of(
					new Row(List.of(new Ingredient(new ItemStack(ModItems.TONGS))),
							Component.empty(), 0, false,
							new Ingredient(new ItemStack(ModItems.HOT_WAKIZASHI_BLADE))),
					new Row(List.of(new Ingredient(new ItemStack(ModItems.HAMMER))),
							Component.empty(), 0, false,
							new Ingredient(new ItemStack(ModItems.HOT_KATANA_BLADE)))));
			return true;
		}
		if (item == ModItems.MOLTEN_KERA || item == ModItems.TAMAHAGANE_INGOT
				|| item == ModItems.FLAT_TAMAHAGANE_INGOT) {
			drawHint(graphics, client, List.of(new Ingredient(new ItemStack(ModItems.HAMMER))),
					Component.empty());
			return true;
		}
		return false;
	}

	/** The item sitting on an anvil in front of the player, or empty. */
	private static ItemStack forgingInView(Minecraft client) {
		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getLookAngle().normalize();

		for (Display.ItemDisplay display : client.level.getEntitiesOfClass(Display.ItemDisplay.class,
				client.player.getBoundingBox().inflate(RANGE))) {
			Vec3 toDisplay = display.position().subtract(eye);
			if (toDisplay.length() > RANGE || look.dot(toDisplay.normalize()) < VIEW_DOT) {
				continue;
			}
			ItemStack stack = display.getItemStack();
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Filled and ready - the only remaining step is striking it alight. */
	private static void drawLightHint(GuiGraphicsExtractor graphics, Minecraft client) {
		drawHint(graphics, client,
				List.of(new Ingredient(new ItemStack(Items.FLINT_AND_STEEL))),
				Component.empty());
	}

	/**
	 * One step of a prompt: either an item (its own count is the amount, drawn
	 * corner-style by itemDecorations) or a gui sprite, for things with no item form
	 * such as water.
	 */
	private record Ingredient(ItemStack stack, Identifier sprite) {
		Ingredient(ItemStack stack) {
			this(stack, null);
		}

		Ingredient(Identifier sprite) {
			this(ItemStack.EMPTY, sprite);
		}
	}

	private static void drawHint(GuiGraphicsExtractor graphics, Minecraft client,
								  List<Ingredient> ingredients, Component tail) {
		drawRows(graphics, client, List.of(new Row(ingredients, tail, 0, true, null)));
	}

	private static void drawHint(GuiGraphicsExtractor graphics, Minecraft client,
								  List<Ingredient> ingredients, Component tail, int clicks) {
		drawRows(graphics, client, List.of(new Row(ingredients, tail, clicks, true, null)));
	}

	private static void drawHint(GuiGraphicsExtractor graphics, Minecraft client,
								  List<Ingredient> ingredients, Component tail, int clicks,
								  boolean plusBeforeTail) {
		drawRows(graphics, client, List.of(new Row(ingredients, tail, clicks, plusBeforeTail, null)));
	}

	/**
	 * One line of a prompt: click, then these things, optionally arrowed to what you
	 * get out of it.
	 *
	 * @param clicks         drawn in the corner of the mouse icon, inventory-style. 0 draws nothing.
	 *                       It belongs on the mouse, not on the item - four strikes is
	 *                       four clicks, not four hammers.
	 * @param plusBeforeTail whether to separate the trailing text with a "+". True when
	 *                       the tail is another step ("+ 60 sec"), false when it
	 *                       describes the item before it ("Tongs in your offhand").
	 * @param outcome        drawn after an arrow, or null. Showing the result is what
	 *                       lets a fork be drawn as two rows instead of explained in
	 *                       a sentence.
	 */
	private record Row(List<Ingredient> ingredients, Component tail, int clicks,
					   boolean plusBeforeTail, Ingredient outcome) {
	}

	/**
	 * Rows are stacked and each centred independently, so a two-row fork reads as two
	 * complete instructions rather than one wrapped sentence. The block as a whole
	 * stays anchored at BOTTOM_OFFSET, growing upward.
	 */
	private static void drawRows(GuiGraphicsExtractor graphics, Minecraft client, List<Row> rows) {
		int rowHeight = MOUSE_H + ROW_GAP;
		int top = graphics.guiHeight() - BOTTOM_OFFSET - (rows.size() - 1) * rowHeight;
		for (int i = 0; i < rows.size(); i++) {
			drawRow(graphics, client, rows.get(i), top + i * rowHeight);
		}
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Minecraft client, Row row, int y) {
		Font font = client.font;

		Component plus = Component.literal("+");
		Component arrow = Component.literal("\u2192");
		int plusWidth = font.width(plus);
		int arrowWidth = font.width(arrow);

		int width = MOUSE_W;
		for (Ingredient ignored : row.ingredients()) {
			width += GAP + plusWidth + GAP + ICON;
		}
		if (row.outcome() != null) {
			width += GAP + arrowWidth + GAP + ICON;
		}
		boolean hasTail = !row.tail().getString().isEmpty();
		if (hasTail) {
			width += GAP + font.width(row.tail());
			if (row.plusBeforeTail()) {
				width += plusWidth + GAP;
			}
		}

		// Centred above the hotbar, where vanilla shows the held item name - somewhere
		// players already scan, and out of the crosshair's way.
		int x = (graphics.guiWidth() - width) / 2;
		int textY = y + (MOUSE_H - font.lineHeight) / 2;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, MOUSE_ICON, x, y, MOUSE_W, MOUSE_H);
		if (row.clicks() > 0) {
			Component count = Component.literal(Integer.toString(row.clicks()));
			graphics.text(font, count,
					x + MOUSE_W - font.width(count) + 2,
					y + MOUSE_H - font.lineHeight + 2,
					0xFFFFFFFF);
		}
		int cursor = x + MOUSE_W;

		for (Ingredient ingredient : row.ingredients()) {
			// A plus between every step, including after the mouse, so the whole
			// prompt reads as one instruction rather than loose icons.
			cursor += GAP;
			graphics.text(font, plus, cursor, textY, 0xFFFFFFFF);
			cursor += plusWidth + GAP;
			cursor = drawIngredient(graphics, font, ingredient, cursor, y);
		}

		if (row.outcome() != null) {
			cursor += GAP;
			graphics.text(font, arrow, cursor, textY, 0xFFFFFFFF);
			cursor += arrowWidth + GAP;
			cursor = drawIngredient(graphics, font, row.outcome(), cursor, y);
		}

		if (hasTail) {
			cursor += GAP;
			if (row.plusBeforeTail()) {
				graphics.text(font, plus, cursor, textY, 0xFFFFFFFF);
				cursor += plusWidth + GAP;
			}
			graphics.text(font, row.tail(), cursor, textY, 0xFFFFFFFF);
		}
	}

	private static int drawIngredient(GuiGraphicsExtractor graphics, Font font, Ingredient ingredient,
									   int cursor, int y) {
		if (ingredient.sprite() != null) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ingredient.sprite(), cursor, y, ICON, ICON);
		} else {
			graphics.item(ingredient.stack(), cursor, y);
			graphics.itemDecorations(font, ingredient.stack(), cursor, y);
		}
		return cursor + ICON;
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
