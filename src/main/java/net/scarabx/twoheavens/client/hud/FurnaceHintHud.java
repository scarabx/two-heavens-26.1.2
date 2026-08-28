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
	// How long one stage of each process lasts, for interpolating between them.
	// Curing 600 ticks / 8, passive half 400 / 4, kera cooling 200 / 8.
	private static final long CURING_STAGE_MS = 3750L;
	private static final long PASSIVE_STAGE_MS = 5000L;
	private static final long COOL_STAGE_MS = 1250L;
	// Bellows phase: 300 ticks in 4 steps.
	private static final int BELLOWS_STEPS = 4;
	private static final long BELLOWS_STAGE_MS = 3750L;
	private static final int BAR_WIDTH = 80;
	private static final int BAR_HEIGHT = 5;
	// The same dark ground vanilla uses behind its own bars.
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
						progress("curing", unfired.getValue(TataraFurnaceBlock.COLOR_STAGE), MAX_STAGE, CURING_STAGE_MS));
				return;
			}
			int missing = UNFIRED_CHARCOAL - unfired.getValue(TataraFurnaceBlock.CHARCOAL_LEVEL);
			if (missing > 0) {
				// The charcoal only. Listing the flint and steel beside it read as two
				// things to bring rather than two steps in order, and this furnace takes
				// exactly one material - so there is nothing here that a second icon
				// clarifies. Once the charcoal is in, the prompt becomes the flint and
				// steel by itself, which is the whole instruction at that point.
				drawHint(graphics, client, List.of(
						new Ingredient(new ItemStack(Items.CHARCOAL, missing))),
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
				// Arrowed only when charcoal is still listed ahead of it. Once the
				// charcoal is full it drops off the row and the satetsu leads, where an
				// arrow would point at nothing.
				ItemStack sand = new ItemStack(ModBlocks.SATETSU_SAND, satetsu);
				missing.add(charcoal > 0 ? Ingredient.then(sand) : new Ingredient(sand));
			}

			if (missing.isEmpty()) {
				drawLightHint(graphics, client);
			} else {
				// Every step of this row is enforced in order - satetsu is refused until
				// the charcoal is full, and the furnace refuses to light until both are -
				// so the whole row is arrows rather than a mix.
				missing.add(Ingredient.then(new ItemStack(Items.FLINT_AND_STEEL)));
				drawHint(graphics, client, missing, Component.empty());
			}
		} else if (fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE) >= BELLOWS_PHASE_STAGE) {
			// Past the passive half - heat now falls without bellows work.
			// redness_stage is a block state, so the client already knows how hot it is -
			// no need to sync the block entity to count down what is left.
			int pumpsLeft = Math.max(0, MAX_REDNESS_STAGE
					- fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE));
			// The tally sits in the bellows' own corner, inventory-style, rather than
			// spelling it out in words - the icon already says what to use, so the
			// number only has to say how many are left.
			//
			// Once the pumps are done the bellows drops out entirely: showing it with
			// no number read as "use this" when there was nothing left to do, and the
			// only thing still outstanding is the clock, which the bar shows.
			if (pumpsLeft > 0) {
				Row main = new Row(
						List.of(Ingredient.counted(new ItemStack(ModItems.BELLOWS), pumpsLeft)),
						Component.empty(), 0, false, null);
				drawRows(graphics, client, List.of(main));
			}
			// The clock, alongside the pump count in the text: finishing needs both, and
			// only the pumps were visible before. Interpolated like the other timed
			// bars, since this one really is driven by time.
			drawBar(graphics, (graphics.guiWidth() - BAR_WIDTH) / 2,
					graphics.guiHeight() - BOTTOM_OFFSET + MOUSE_H + ROW_GAP,
					progress("bellows", fired.getValue(TataraFurnaceFiredBlock.BELLOWS_PROGRESS),
							BELLOWS_STEPS, BELLOWS_STAGE_MS));

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
					progress("passive", fired.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE),
							PASSIVE_REDNESS_STAGE, PASSIVE_STAGE_MS));
		}
	}

	/**
	 * Holding a hot blade with tongs in the offhand: the only remaining step is to
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

	private static int drawWithHint(GuiGraphicsExtractor graphics, Minecraft client,
									  ItemStack wanted, Component tail, int clicks,
									  boolean plusBeforeTail) {
		Row main = new Row(needed(client, wanted), tail, clicks, plusBeforeTail, null);
		if (!canOffer(client, wanted) || ShiftState.isDown()) {
			drawRows(graphics, client, List.of(main));
			return 1;
		}
		// The hint gets its own line: replacing the tail with it threw away what the
		// prompt was actually telling you.
		drawRows(graphics, client, List.of(main,
				Row.text(Component.translatable("hud.twoheavens.shift_for_recipe"),
						firstIconX(client.font, main, graphics.guiWidth()))));
		return 2;
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

		// cool_stage counts up as it cools, which is already progress toward being
		// breakable - so the bar uses it directly and fills, like every other one.
		float remaining = progress("kera", kera.getValue(KeraBlock.COOL_STAGE), COOL_STAGE_MAX, COOL_STAGE_MS);

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

	// Where the bar had got to when the stage last changed, so it can be carried
	// smoothly across to the next one instead of jumping.
	private static String barKey = "";
	private static int barStage = -1;
	private static long barStageStartMs = 0L;

	/**
	 * Stage as a 0-1 fraction, interpolated ACROSS each stage rather than stepping.
	 *
	 * Stage properties only change 4 or 8 times over a whole process, and the final
	 * one lands on the same tick as the state change - so a bar driven straight off
	 * them was full for an instant at best, and looked like it vanished before
	 * finishing. Rounding up to full early would fix the look by lying: the bar would
	 * read done while the block was still busy.
	 *
	 * Filling continuously between stages fixes it without either problem. The bar
	 * sweeps up to full exactly as the process ends, so the last moment is visible
	 * because it is moving, not because it was reached sooner.
	 */
	private static float progress(String key, int stage, int max, long stageMillis) {
		long now = System.currentTimeMillis();
		if (!key.equals(barKey) || stage != barStage) {
			barKey = key;
			barStage = stage;
			barStageStartMs = now;
		}
		float within = Math.min(1.0F, (now - barStageStartMs) / (float) stageMillis);
		return Math.min(1.0F, (stage + within) / (float) max);
	}

	/**
	 * A filled bar, 0 to 1. Durations are shown this way rather than as a number of
	 * seconds: a bar answers "how much longer" at a glance and cannot go stale when
	 * the timing is retuned, which is what happened to the old "60 sec" labels.
	 *
	 * Every bar FILLS toward completion, whatever it is timing. A bar answers one
	 * question - how close am I to being able to act - and that question is the same
	 * whether a furnace is heating or a kera is cooling, so the answer should look the
	 * same. A draining bar reads as something running out, which is the wrong story
	 * for a wait that ends in something becoming available.
	 *
	 * The colour carries the same meaning as the length: red when nothing can be done
	 * yet, through orange and yellow, to green when it is ready.
	 */
	private static void drawBar(GuiGraphicsExtractor graphics, int x, int y, float fraction) {
		float clamped = Math.max(0.0F, Math.min(1.0F, fraction));
		graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BAR_BACKGROUND);
		graphics.fill(x, y, x + Math.round(BAR_WIDTH * clamped), y + BAR_HEIGHT, barColor(clamped));
	}

	/** Red, orange, yellow, green - stepped through as the bar fills. */
	private static final int[] BAR_STOPS = {0xFFD03A2E, 0xFFE0762A, 0xFFE0C62A, 0xFF3CE04A};

	private static int barColor(float fraction) {
		float scaled = fraction * (BAR_STOPS.length - 1);
		int index = Math.min(BAR_STOPS.length - 2, (int) scaled);
		return blend(BAR_STOPS[index], BAR_STOPS[index + 1], scaled - index);
	}

	private static int blend(int from, int to, float t) {
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
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
	private record Ingredient(ItemStack stack, Identifier sprite, int badge, boolean afterArrow) {
		Ingredient(ItemStack stack) {
			this(stack, null, 0, false);
		}

		Ingredient(Identifier sprite) {
			this(ItemStack.EMPTY, sprite, 0, false);
		}

		/**
		 * An item with a count drawn in its corner, inventory-style. Used for a
		 * remaining tally - unlike a stack's own count, this shows even at 1, which
		 * vanilla hides.
		 */
		static Ingredient counted(ItemStack stack, int badge) {
			return new Ingredient(stack, null, badge, false);
		}

		/**
		 * Joined to the step before it with an arrow rather than a plus, for the rare
		 * case where the game enforces an order. "+" is set grammar - it reads as a
		 * list of things to bring, which is right for everything else here, and wrong
		 * for the tatara's fill, where satetsu is refused until the charcoal is full.
		 */
		static Ingredient then(ItemStack stack) {
			return new Ingredient(stack, null, 0, true);
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
		int arrowWidth = font.width(Component.literal("\u2192"));
		int width = row.mouse() ? MOUSE_W : 0;
		boolean first = true;
		for (Ingredient ingredient : row.ingredients()) {
			// A plus joins one thing to the next, so the first item only gets one when
			// the mouse icon precedes it. A row without the mouse used to open with a
			// stray leading plus.
			if (!first || row.mouse()) {
				width += GAP + (ingredient.afterArrow() && !first ? arrowWidth : plusWidth);
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
				// An arrow only when there is a previous step for it to point away
				// from; as the leading separator after the mouse icon it would read as
				// "click produces this".
				boolean sequence = ingredient.afterArrow() && !firstDrawn;
				graphics.text(font, sequence ? arrow : plus, cursor, textY, 0xFFFFFFFF);
				cursor += (sequence ? arrowWidth : plusWidth) + GAP;
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
		if (ingredient.badge() > 0) {
			Component count = Component.literal(Integer.toString(ingredient.badge()));
			graphics.text(font, count,
					cursor + ICON - font.width(count),
					y + ICON - font.lineHeight + 1,
					0xFFFFFFFF);
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
