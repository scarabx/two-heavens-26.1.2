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
import net.scarabx.twoheavens.block.custom.KeraBlock;
import net.scarabx.twoheavens.block.custom.TataraFurnaceBlock;
import net.scarabx.twoheavens.block.custom.TataraFurnaceFiredBlock;
import net.scarabx.twoheavens.item.ModItems;
import net.scarabx.twoheavens.item.ModRecipeTooltips;
import net.scarabx.twoheavens.item.ShiftState;

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
	/** cool_stage's maximum - the kera is fully cool here. */
	private static final int COOL_STAGE_MAX = 8;
	/** redness_stage at full heat - what is left below it is pumps still owed. */
	private static final int MAX_REDNESS_STAGE = 8;
	/** Stage the passive half tops out at - half of MAX_REDNESS_STAGE. */
	private static final int PASSIVE_REDNESS_STAGE = 4;
	/** Highest value of the unfired furnace's colour_stage. */
	private static final int MAX_STAGE = 8;
	private static final int BAR_WIDTH = 80;
	private static final int BAR_HEIGHT = 5;
	// Hot orange draining away, on the same dark ground vanilla uses behind its bars.
	private static final int BAR_FILL = 0xFFFF7A2E;
	private static final int BAR_BACKGROUND = 0xFF1A1A1A;
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

		if (drawCoolingHint(graphics, client)) {
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

		BlockState unfired = blockInView(client, ModBlocks.TATARA_FURNACE);
		if (unfired != null) {
			if (unfired.getValue(TataraFurnaceBlock.LIT)) {
				// Curing: just the bar. A heads-up here had nothing new to say - the
				// next stage wants charcoal, satetsu and a flint and steel, and you
				// cannot be watching a curing furnace without already owning the flint
				// and steel that lit it, while the join hints cover the rest.
				//
				// A heads-up earns its place only when the next step needs a TOOL the
				// player probably lacks. Materials are gathered; tools must be crafted,
				// which is what makes discovering you need one mid-process irritating.
				drawBar(graphics, (graphics.guiWidth() - BAR_WIDTH) / 2,
						graphics.guiHeight() - BOTTOM_OFFSET,
						unfired.getValue(TataraFurnaceBlock.COLOR_STAGE) / (float) MAX_STAGE);
				return;
			}
			int missing = UNFIRED_CHARCOAL - unfired.getValue(TataraFurnaceBlock.CHARCOAL_LEVEL);
			if (missing > 0) {
				// Both remaining steps at once - the charcoal it still wants, then the
				// flint and steel. Showing a duration here was answering a question
				// nobody had yet.
				drawHint(graphics, client, List.of(
						new Ingredient(new ItemStack(Items.CHARCOAL, missing)),
						new Ingredient(new ItemStack(Items.FLINT_AND_STEEL))),
						Component.empty());
			} else {
				drawLightHint(graphics, client);
			}
			return;
		}

		BlockState fired = blockInView(client, ModBlocks.TATARA_FURNACE_FIRED);
		if (fired == null) {
			return;
		}
		if (fired.getValue(TataraFurnaceFiredBlock.KERA_FORMED)) {
			// The kera is ready but sealed in. Four hammer strikes break it out, and
			// nothing about the block says so - this is where the guidance used to
			// stop entirely.
			drawWithHint(graphics, client, new ItemStack(ModItems.HAMMER),
					Component.empty(), KERA_HAMMER_STRIKES, false);
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
				missing.add(new Ingredient(new ItemStack(Items.FLINT_AND_STEEL)));
				drawHint(graphics, client, missing, Component.empty());
			}
		} else if (fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE) >= BELLOWS_PHASE_STAGE) {
			// Past the passive half - heat now falls without bellows work.
			// redness_stage is a block state, so the client already knows how hot it is -
			// no need to sync the block entity to count down what is left.
			int pumpsLeft = Math.max(0, MAX_REDNESS_STAGE
					- fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE));
			drawWithHint(graphics, client, new ItemStack(ModItems.BELLOWS),
					Component.translatable(pumpsLeft == 1
							? "hud.twoheavens.furnace_bellows_one"
							: "hud.twoheavens.furnace_bellows", pumpsLeft),
					0, true);
		}
		// Lit but still in the passive half: nothing to do yet, so the wait is spent
		// warning that a bellows is about to be needed.
		else {
			// Passive half: heat is climbing on its own, so show how far along it is
			// alongside the bellows it is about to need.
			// Both tools the rest of the chain needs, named in the one window with idle
			// time to act on them: the bellows for the half about to start, and the
			// hammer to break the kera out afterwards. The hammer had no warning at all
			// before - it appeared as a current-step prompt at the moment it was
			// already required.
			drawComingUp(graphics, client,
					List.of(new ItemStack(ModItems.BELLOWS), new ItemStack(ModItems.HAMMER)),
					fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE)
							/ (float) PASSIVE_REDNESS_STAGE);
		}
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
			drawWithHint(graphics, client, new ItemStack(ModItems.TONGS),
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

	/**
	 * Turns a required item into prompt steps.
	 *
	 * Normally just the item. If the player has none of it, holding Shift swaps it for
	 * its ingredients - the same "Hold [Shift] for recipe" gesture every tooltip in the
	 * mod already teaches, so it transfers without being explained again.
	 *
	 * Shift-gated rather than always shown, because a prompt is meant to be a glance:
	 * three ingredient icons in place of one tool turns it into something you have to
	 * read. You cannot hover an item you do not own, so the recipe still has to be
	 * reachable here - just not in the way.
	 */
	private static List<Ingredient> needed(Minecraft client, ItemStack wanted) {
		if (!ShiftState.isDown()
				|| client.player.getInventory().contains(stack -> stack.is(wanted.getItem()))) {
			return List.of(new Ingredient(wanted));
		}
		List<ItemStack> parts = ModRecipeTooltips.ingredientsFor(wanted.getItem());
		if (parts.isEmpty()) {
			return List.of(new Ingredient(wanted));
		}
		List<Ingredient> steps = new ArrayList<>(parts.size());
		for (ItemStack part : parts) {
			steps.add(new Ingredient(part));
		}
		return steps;
	}

	/**
	 * The tail for a prompt naming an item the player may not have.
	 *
	 * Says "[Shift] for recipe" only when there is one to offer - the player lacks the
	 * item and it is craftable - so the common case, where you already have the tool
	 * and are simply being told to use it, stays a bare icon.
	 *
	 * The hint has to be spelled out: the tooltips teach this gesture by writing it in
	 * words, and nobody infers it there either. Assuming it transfers to a different
	 * surface with no prompt was wishful.
	 */
	private static boolean canOffer(Minecraft client, ItemStack wanted) {
		return !client.player.getInventory().contains(stack -> stack.is(wanted.getItem()))
				&& !ModRecipeTooltips.ingredientsFor(wanted.getItem()).isEmpty();
	}

	private static void drawWithHint(GuiGraphicsExtractor graphics, Minecraft client,
									  ItemStack wanted, Component tail, int clicks,
									  boolean plusBeforeTail) {
		Row main = new Row(needed(client, wanted), tail, clicks, plusBeforeTail, null);
		if (!canOffer(client, wanted) || ShiftState.isDown()) {
			drawRows(graphics, client, List.of(main));
			return;
		}
		// The hint gets its own line: replacing the tail with it threw away what the
		// prompt was actually telling you.
		drawRows(graphics, client, List.of(main,
				Row.text(Component.translatable("hud.twoheavens.shift_for_recipe"),
						firstIconX(client.font, main, graphics.guiWidth()))));
	}

	/**
	 * A heads-up shown during the waiting periods, when there is nothing to do and the
	 * prompt would otherwise be blank: these are what the NEXT stage will ask for.
	 *
	 * No mouse icon - it is not an instruction, and showing one would suggest there is
	 * something to click now.
	 */
	private static void drawComingUp(GuiGraphicsExtractor graphics, Minecraft client,
									  List<ItemStack> upcoming, float progress) {
		drawComingUp(graphics, client, upcoming);
		// Under the row, in the slot the second row would occupy.
		int y = graphics.guiHeight() - BOTTOM_OFFSET + MOUSE_H + ROW_GAP;
		drawBar(graphics, (graphics.guiWidth() - BAR_WIDTH) / 2, y, progress);
	}

	private static void drawComingUp(GuiGraphicsExtractor graphics, Minecraft client,
									  List<ItemStack> upcoming) {
		// Waiting is the best moment to make what you are about to need, so the same
		// Shift gesture works here: it expands anything you do not have into how to
		// make it. Only items with a recipe can offer that - satetsu is mined and
		// flint and steel is vanilla, so those stay plain icons.
		List<Ingredient> steps = new ArrayList<>();
		boolean anyOffered = false;
		for (ItemStack wanted : upcoming) {
			steps.addAll(needed(client, wanted));
			anyOffered |= canOffer(client, wanted);
		}

		Row main = new Row(steps, Component.translatable("hud.twoheavens.coming_up"),
				0, false, null, false, null);
		if (!anyOffered || ShiftState.isDown()) {
			drawRows(graphics, client, List.of(main));
			return;
		}
		drawRows(graphics, client, List.of(main,
				Row.text(Component.translatable("hud.twoheavens.shift_for_recipe"),
						firstIconX(client.font, main, graphics.guiWidth()))));
	}

	/**
	 * A cooling kera cannot be broken - the wait is the step. That only works if the
	 * player is told, so this shows a bar that empties as it cools: an unbreakable
	 * block with no explanation is indistinguishable from a bug.
	 *
	 * A bar rather than a number because the point is "nearly there", not a countdown
	 * to read.
	 */
	private static boolean drawCoolingHint(GuiGraphicsExtractor graphics, Minecraft client) {
		BlockState kera = blockInView(client, ModBlocks.KERA_BLOCK);
		if (kera == null) {
			return false;
		}

		// cool_stage counts up as it cools, so the remaining time is what is left of it.
		int stage = kera.getValue(KeraBlock.COOL_STAGE);
		float remaining = 1.0F - (stage / (float) COOL_STAGE_MAX);

		Font font = client.font;
		Component label = Component.translatable("hud.twoheavens.kera_cooling");
		int labelWidth = font.width(label);
		int width = Math.max(labelWidth, BAR_WIDTH);
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() - BOTTOM_OFFSET;

		graphics.text(font, label, x + (width - labelWidth) / 2, y, 0xFFFFFFFF);

		drawBar(graphics, x + (width - BAR_WIDTH) / 2, y + font.lineHeight + 3, remaining);
		return true;
	}

	/** A filled bar, 0 to 1. Durations are shown this way rather than as a number of
	 * seconds: a bar answers "how much longer" at a glance and cannot go stale when
	 * the timing is retuned, which is what happened to the old "60 sec" labels. */
	private static void drawBar(GuiGraphicsExtractor graphics, int x, int y, float fraction) {
		float clamped = Math.max(0.0F, Math.min(1.0F, fraction));
		graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BAR_BACKGROUND);
		graphics.fill(x, y, x + Math.round(BAR_WIDTH * clamped), y + BAR_HEIGHT, BAR_FILL);
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
					   boolean plusBeforeTail, Ingredient outcome, boolean mouse, Integer alignX) {

		Row(List<Ingredient> ingredients, Component tail, int clicks,
				boolean plusBeforeTail, Ingredient outcome) {
			this(ingredients, tail, clicks, plusBeforeTail, outcome, true, null);
		}

		/**
		 * A bare line of text - no mouse icon, no items - drawn at an absolute x rather
		 * than centred, so the Shift hint sits under the item it refers to instead of
		 * wandering with its own width.
		 */
		static Row text(Component text, int alignX) {
			return new Row(List.of(), text, 0, false, null, false, alignX);
		}
	}

	/**
	 * Rows are stacked and each centred independently, so a two-row fork reads as two
	 * complete instructions rather than one wrapped sentence.
	 *
	 * The FIRST row is anchored at BOTTOM_OFFSET and extra rows grow downward. Growing
	 * upward instead made the main message jump up the moment a second row appeared -
	 * so walking up to a furnace without a bellows moved the prompt you were reading.
	 */
	private static void drawRows(GuiGraphicsExtractor graphics, Minecraft client, List<Row> rows) {
		int rowHeight = MOUSE_H + ROW_GAP;
		int top = graphics.guiHeight() - BOTTOM_OFFSET;
		for (int i = 0; i < rows.size(); i++) {
			drawRow(graphics, client, rows.get(i), top + i * rowHeight);
		}
	}

	private static int rowWidth(Font font, Row row) {
		int plusWidth = font.width(Component.literal("+"));
		int width = row.mouse() ? MOUSE_W : 0;
		boolean first = true;
		for (Ingredient ignored : row.ingredients()) {
			// A plus joins one thing to the next, so the first item only gets one when
			// the mouse icon precedes it. A row without the mouse used to open with a
			// stray leading plus.
			if (!first || row.mouse()) {
				width += GAP + plusWidth;
			}
			width += GAP + ICON;
			first = false;
		}
		if (row.outcome() != null) {
			width += GAP + font.width(Component.literal("\u2192")) + GAP + ICON;
		}
		if (!row.tail().getString().isEmpty()) {
			width += GAP + font.width(row.tail());
			if (row.plusBeforeTail()) {
				width += plusWidth + GAP;
			}
		}
		return width;
	}

	/** Where the first item icon lands in a centred row - what the hint aligns to. */
	private static int firstIconX(Font font, Row row, int guiWidth) {
		int x = (guiWidth - rowWidth(font, row)) / 2;
		return x + (row.mouse() ? MOUSE_W : 0) + GAP + font.width(Component.literal("+")) + GAP;
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Minecraft client, Row row, int y) {
		Font font = client.font;

		Component plus = Component.literal("+");
		Component arrow = Component.literal("\u2192");
		int plusWidth = font.width(plus);
		int arrowWidth = font.width(arrow);

		int width = rowWidth(font, row);
		boolean hasTail = !row.tail().getString().isEmpty();

		// Centred above the hotbar, where vanilla shows the held item name - somewhere
		// players already scan, and out of the crosshair's way.
		int x = row.alignX() != null ? row.alignX() : (graphics.guiWidth() - width) / 2;
		int textY = y + (MOUSE_H - font.lineHeight) / 2;

		if (row.mouse()) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, MOUSE_ICON, x, y, MOUSE_W, MOUSE_H);
		}
		if (row.mouse() && row.clicks() > 0) {
			Component count = Component.literal(Integer.toString(row.clicks()));
			graphics.text(font, count,
					x + MOUSE_W - font.width(count) + 2,
					y + MOUSE_H - font.lineHeight + 2,
					0xFFFFFFFF);
		}
		int cursor = row.mouse() ? x + MOUSE_W : x;

		boolean firstDrawn = true;
		for (Ingredient ingredient : row.ingredients()) {
			// A plus between every step, including after the mouse, so the whole
			// prompt reads as one instruction rather than loose icons - but never
			// before the first item of a row that has no mouse icon.
			cursor += GAP;
			if (!firstDrawn || row.mouse()) {
				graphics.text(font, plus, cursor, textY, 0xFFFFFFFF);
				cursor += plusWidth + GAP;
			}
			cursor = drawIngredient(graphics, font, ingredient, cursor, y);
			firstDrawn = false;
		}

		if (row.outcome() != null) {
			cursor += GAP;
			graphics.text(font, arrow, cursor, textY, 0xFFFFFFFF);
			cursor += arrowWidth + GAP;
			cursor = drawIngredient(graphics, font, row.outcome(), cursor, y);
		}

		if (hasTail) {
			if (row.mouse() || !row.ingredients().isEmpty()) {
				cursor += GAP;
			}
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
	private static BlockState blockInView(Minecraft client, net.minecraft.world.level.block.Block block) {
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
