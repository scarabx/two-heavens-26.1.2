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
import net.scarabx.twoheavens.client.tooltip.ClientRecipeTooltip;
import net.scarabx.twoheavens.item.ModRecipeTooltips;
import net.scarabx.twoheavens.item.RecipeTooltipData;
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
	/** How far the click count sits past the mouse icon's bottom-right corner. */
	private static final int COUNT_NUDGE = 4;

	/** How far away the furnace can be and still prompt. */
	private static final double RANGE = 8.0;
	/** cos of the half-angle counted as "looking at it" - about 40 degrees off-centre. */
	private static final double VIEW_DOT = 0.76;

	private static final int UNFIRED_CHARCOAL = 8;
	private static final int FIRED_CHARCOAL = 4;
	private static final int FIRED_SATETSU = 4;
	/** redness_stage once heat reaches the passive cap - i.e. the bellows half has begun. */
	private static final int BELLOWS_PHASE_STAGE = 4;
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
	/**
	 * Extra air between the two rows of the anvil fork. They are alternatives, not a
	 * sequence, and at the normal row gap they read as one four-icon instruction. The
	 * TOP row lifts by this much and the bottom stays put, so the block grows away from
	 * the hotbar rather than towards it.
	 */
	private static final int FORK_GAP = 10;

	/** How far the recipe grid sits from the left edge - clear of the furnace, clear of the bezel. */
	private static final int GRID_MARGIN = 8;

	/** Vertical space between stacked rows of a multi-line prompt. */
	private static final int ROW_GAP = 4;
	/**
	 * Height above the bottom edge. Kept well clear of the status bars, which stack
	 * upward as the player gains armour and air, rather than hugging the hotbar.
	 */
	private static final int BOTTOM_OFFSET = 96;

	/**
	 * Extra height for the anvil's prompts only.
	 *
	 * At the furnace the prompt covers the SIDE of the block, which shows nothing - the
	 * smelt reads through the top opening, the smoke and the glow, all of which stay
	 * clear. At the anvil it covers the BLADE, and the blade is the whole point: the
	 * anvil sequence is four reveals in a row, and the silhouette is how you know which
	 * stage you are on. You also look down at an anvil, which pushes it into the lower
	 * half of the screen exactly where the HUD lives.
	 *
	 * Same rule the recipe grid follows: reference material does not cover the content.
	 */
	private static final int ANVIL_LIFT = 24;

	/** How close to the top of the screen an anchored prompt may get before it pins. */
	private static final int TOP_MARGIN = 20;

	/**
	 * Pixels the cooling kera's prompt sits BELOW its anchor - negative rise.
	 *
	 * The other way up from the anvil, and for the same reason: put the prompt where
	 * the thing worth watching is not. A kera cracks open as it cools and the block
	 * itself is the progress indicator, so the prompt goes under it rather than across
	 * it. The anvil's blade sits on top of its block, so that one goes above.
	 */
	private static final int KERA_BELOW = 46;

	/** Pixels the anvil's prompt sits ABOVE its anchor - clear of the blade on top. */
	private static final int ANVIL_ABOVE = 30;

	/**
	 * The world point this frame's prompt is pinned to, or null to fall back to the
	 * fixed screen position.
	 *
	 * Block-anchored rather than screen-anchored because no constant screen offset can
	 * work: the block's position on screen is a function of BOTH where you look and how
	 * far away you stand, so any fixed lift clears it at one camera angle and is covered
	 * at another. Looking down at an anvil defeated every value tried.
	 */
	private static Vec3 anchor = null;

	/** Pixels above the projected anchor to place the first row. */
	private static int anchorRise = 0;

	/**
	 * World point to GUI coordinates, or null when it is behind the camera or off
	 * screen - in which case the caller keeps the old fixed placement rather than
	 * drawing the prompt somewhere absurd.
	 */
	private static Integer projectY(Minecraft client, GuiGraphicsExtractor graphics, Vec3 world) {
		Vec3 eye = client.player.getEyePosition();
		Vec3 fwd = client.player.getLookAngle().normalize();
		Vec3 right = fwd.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			return null;
		}
		right = right.normalize();
		Vec3 up = right.cross(fwd).normalize();

		Vec3 to = world.subtract(eye);
		double depth = to.dot(fwd);
		if (depth <= 0.05) {
			return null;
		}
		double tanHalf = Math.tan(Math.toRadians(client.options.fov().get()) / 2.0);
		double ndcY = (to.dot(up) / depth) / tanHalf;
		int y = (int) Math.round((0.5 - ndcY * 0.5) * graphics.guiHeight());
		return (y < -graphics.guiHeight() || y > graphics.guiHeight() * 2) ? null : y;
	}

	/**
	 * Cancels the camera drop while sneaking, for EVERY block prompt.
	 *
	 * Sneaking lowers eye height, so the block rises in view while a screen-anchored
	 * prompt stays put, eating the clearance ANVIL_LIFT bought. It bites hardest at the
	 * anvil, because holding Shift to open a recipe grid IS sneaking.
	 *
	 * Small on purpose. A first attempt used 12 on top of a 44 anvil lift; 44 was
	 * already touching the crosshair on its own, so the total put the prompt over the
	 * aiming point. At 8 on top of 24 the sneaking total is 32, well clear.
	 */
	private static final int SNEAK_LIFT = 8;

	/**
	 * Set per frame - ANVIL_LIFT while drawing anvil prompts, zero everywhere else.
	 * Every vertical anchor goes through {@link #top}, so the whole block (rows, bar and
	 * recipe grids alike) moves together rather than drifting apart.
	 */
	private static int lift = 0;

	/** The y of the first prompt row, lifted when the anvil is what is being drawn. */
	private static int top(GuiGraphicsExtractor graphics) {
		if (anchor != null) {
			Integer y = projectY(Minecraft.getInstance(), graphics, anchor);
			if (y != null) {
				// Clamped only at the very edges of the screen. A clamped value is a FIXED
				// screen position, and anything fixed on screen looks like it is following
				// your head while the world slides past - so the clamp must not engage
				// while the block is still in the view cone. A half-screen limit did, at
				// once: the anvil projects near centre whenever you look at it, and the
				// rise pushed it straight past.
				return Math.max(TOP_MARGIN,
						Math.min(y - anchorRise, graphics.guiHeight() - BOTTOM_OFFSET / 2));
			}
		}
		return graphics.guiHeight() - BOTTOM_OFFSET - lift;
	}

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

		// Reset before anything reads it, so no prompt can inherit the previous frame's
		// value - the cooling and quench hints below run before the anvil branch sets it.
		lift = client.player.isCrouching() ? SNEAK_LIFT : 0;
		anchor = null;
		anchorRise = 0;

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
						top(graphics),
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
			// The tally sits on the MOUSE, not on the bellows. One rule across the whole
			// HUD: a number on the mouse is clicks, a number on an item is how many of
			// that item you need. It used to sit in the bellows' own corner, which read
			// correctly on its own but taught a second convention for the same idea -
			// the kera's four hammer strikes are drawn the other way, and an
			// inventory-style corner number means item quantity everywhere else in the
			// game.
			//
			// Once the pumps are done the bellows drops out entirely: showing it with
			// no number read as "use this" when there was nothing left to do, and the
			// only thing still outstanding is the clock, which the bar shows.
			if (pumpsLeft > 0) {
				// Through drawWithHint so it offers the bellows' recipe to anyone who
				// still has none. The passive half already named it in the heads-up,
				// where there was idle time to go and craft one - but a player who
				// missed that arrives here with no route to it, and this row is the
				// last place the bellows is ever mentioned.
				drawWithHint(graphics, client, new ItemStack(ModItems.BELLOWS),
						Component.empty(), pumpsLeft, false);
			}
			// The clock, alongside the pump count in the text: finishing needs both, and
			// only the pumps were visible before. Interpolated like the other timed
			// bars, since this one really is driven by time.
			drawBar(graphics, (graphics.guiWidth() - BAR_WIDTH) / 2,
					top(graphics) + MOUSE_H + ROW_GAP,
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

		// No water icon at all. There is no water ITEM to draw, and every substitute
		// misleads: a bucket says fetch one and use it, when you quench in a block in
		// the world. Correcting a wrong icon with a word is a workaround that shows its
		// working - and the word only helps if you read it, at which point the icon was
		// carrying nothing.
		//
		// So the icon carries what an icon is good at - the thing in your hand, drawn as
		// the player's own blade - and the words carry the verb and the target, which
		// icons cannot express. Shorter than the bucket version, and made only of this
		// mod's own art.
		drawHint(graphics, client,
				List.of(new Ingredient(new ItemStack(held.getItem()))),
				Component.translatable("hud.twoheavens.quench"), 0, false);
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
		lift += ANVIL_LIFT;

		Item item = forging.getItem();
		if (item == ModItems.HOT_KATANA_BLADE) {
			// Finished. Only the tongs remain, and picking it up bare-handed burns.
			//
			// The icon alone - naming the tongs in words beside a picture of them said
			// the same thing twice, and a prompt is meant to be a glance. Getting it
			// wrong is caught by the overlay message the anvil sends, which is where the
			// offhand requirement is spelled out, at the moment it actually matters.
			drawWithHint(graphics, client, new ItemStack(ModItems.TONGS),
					Component.empty(), 0, false);
			return true;
		}
		if (item == ModItems.HOT_WAKIZASHI_BLADE) {
			// A fork rather than a step. Two complete rows, each ending in what it
			// produces, so the choice is visible - two icons on one line would not say
			// which did what, and a sentence made the player read mid-fight.
			//
			// Each row is NAMED, because the two outcomes are both hot blades and the
			// icons differ only in silhouette. The name is the weapon, not the item -
			// "Wakizashi", not "Hot Wakizashi Blade". The question being asked here is
			// which sword you are making; that it arrives hot and needs quenching is the
			// next prompt's business, and spelling it out twice turns a glance into a
			// read.
			ItemStack tongs = new ItemStack(ModItems.TONGS);
			ItemStack hammer = new ItemStack(ModItems.HAMMER);
			List<Row> rows = new ArrayList<>(3);
			rows.add(new Row(List.of(new Ingredient(tongs)),
					Component.translatable("hud.twoheavens.fork_wakizashi"), 0, false,
					new Ingredient(new ItemStack(ModItems.HOT_WAKIZASHI_BLADE))));
			rows.add(new Row(List.of(new Ingredient(hammer)),
					Component.translatable("hud.twoheavens.fork_katana"), 0, false,
					new Ingredient(new ItemStack(ModItems.HOT_KATANA_BLADE))));

			// Both branches need a tool, and at this point the tongs in particular are
			// very likely still uncrafted - nothing before now has required them. So the
			// fork offers the recipe the same way every other prompt does.
			List<ItemStack> lacking = new ArrayList<>(2);
			if (canOffer(client, tongs)) {
				lacking.add(tongs);
			}
			if (canOffer(client, hammer)) {
				lacking.add(hammer);
			}
			if (!lacking.isEmpty() && !ShiftState.isDown()) {
				rows.add(Row.text(Component.translatable("hud.twoheavens.shift_for_recipe")));
			}
			drawRows(graphics, client, rows, FORK_GAP);
			if (!lacking.isEmpty() && ShiftState.isDown()) {
				drawRecipeGrids(graphics, client, lacking);
			}
			return true;
		}
		if (item == ModItems.MOLTEN_KERA || item == ModItems.TAMAHAGANE_INGOT
				|| item == ModItems.FLAT_TAMAHAGANE_INGOT) {
			// Through drawWithHint, not drawHint, so it offers the hammer's recipe when
			// the player has none. This branch built its row directly and bypassed
			// canOffer - the same gap the fork had - and it is the worse place for it:
			// this is the FIRST anvil step, so someone who set a kera down without a
			// hammer was told to use one with no way to find out how to get it.
			drawWithHint(graphics, client, new ItemStack(ModItems.HAMMER),
					Component.empty(), 0, false);
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
				// Anchored to the blade itself and raised clear of it: the anvil sequence
				// is four reveals and the silhouette is how you know which stage you are on.
				anchor = display.position();
				anchorRise = ANVIL_ABOVE;
				return stack;
			}
		}
		return ItemStack.EMPTY;
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

	/**
	 * Draws the real crafting grid for anything the player still needs, using the very
	 * same renderer the tooltips use - a loose row of ingredient icons with counts told
	 * you WHAT went in but never the shape, and the shape is most of a recipe.
	 *
	 * Drawn to the LOWER LEFT, deliberately out of the centre.
	 *
	 * Two earlier placements both failed. Downward there are only BOTTOM_OFFSET pixels
	 * of room, so the second of two grids (the passive half wants bellows AND hammer)
	 * was drawn off the bottom edge entirely. Upward it fitted, but landed squarely over
	 * the furnace - and the furnace mid-smelt, with its smoke and its glow, is the thing
	 * the player is actually there to watch. A recipe is reference material; it does not
	 * get the middle of the screen.
	 *
	 * Bottom-aligned to just above the prompt row rather than free-floating, so it still
	 * reads as belonging to that line even from off-centre. The centred prompt text is
	 * what ties the two together.
	 *
	 * The progress bar is unaffected either way: it sits below the row, and the grid
	 * never enters that space.
	 */
	private static void drawRecipeGrids(GuiGraphicsExtractor graphics, Minecraft client,
										List<ItemStack> wanted) {
		List<RecipeTooltipData.Entry> entries = new ArrayList<>();
		for (ItemStack stack : wanted) {
			if (client.player.getInventory().contains(s -> s.is(stack.getItem()))) {
				continue;
			}
			RecipeTooltipData data = ModRecipeTooltips.madeFrom(stack.getItem());
			if (data != null) {
				entries.addAll(data.entries());
			}
		}
		if (entries.isEmpty()) {
			return;
		}

		ClientRecipeTooltip grid = new ClientRecipeTooltip(new RecipeTooltipData(List.copyOf(entries)));
		int width = grid.getWidth(client.font);
		int height = grid.getHeight(client.font);
		// Deliberately NOT lifted with the prompt rows. ANVIL_LIFT exists to keep the
		// prompt off the blade; the grid is already out of the way in the lower left
		// corner, where nothing is competing with it, so raising it too would move it
		// for no reason and open a gap under the prompt it belongs to.
		int y = graphics.guiHeight() - BOTTOM_OFFSET - height - ROW_GAP;
		grid.extractImage(client.font, GRID_MARGIN, y, width, height, graphics);
	}

	private static int drawWithHint(GuiGraphicsExtractor graphics, Minecraft client,
									  ItemStack wanted, Component tail, int clicks,
									  boolean plusBeforeTail) {
		Row main = new Row(List.of(new Ingredient(wanted)), tail, clicks, plusBeforeTail, null);
		if (!canOffer(client, wanted)) {
			drawRows(graphics, client, List.of(main));
			return 1;
		}
		if (ShiftState.isDown()) {
			// The prompt keeps its own line and the grid opens above it, so the row
			// still says what you are being told to use and does not move.
			drawRows(graphics, client, List.of(main));
			drawRecipeGrids(graphics, client, List.of(wanted));
			return 1;
		}
		// The hint gets its own line: replacing the tail with it threw away what the
		// prompt was actually telling you.
		drawRows(graphics, client, List.of(main, Row.text(
				Component.translatable("hud.twoheavens.shift_for_recipe"))));
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
		// Under whatever was actually drawn, not under a fixed row count. This used to
		// assume one row and put the bar in the second slot - which is exactly where the
		// Shift prompt goes when it is showing, so the two drew on top of each other
		// through the whole passive half of the smelt.
		int rows = drawComingUp(graphics, client, upcoming);
		int y = top(graphics) + rows * (MOUSE_H + ROW_GAP);
		drawBar(graphics, (graphics.guiWidth() - BAR_WIDTH) / 2, y, progress);
	}

	/** @return how many rows were drawn, so a caller can place anything below them. */
	private static int drawComingUp(GuiGraphicsExtractor graphics, Minecraft client,
									  List<ItemStack> upcoming) {
		// Waiting is the best moment to make what you are about to need, so the same
		// Shift gesture works here: it expands anything you do not have into how to
		// make it. Only items with a recipe can offer that - satetsu is mined and
		// flint and steel is vanilla, so those stay plain icons.
		List<Ingredient> steps = new ArrayList<>();
		boolean anyOffered = false;
		for (ItemStack wanted : upcoming) {
			steps.add(new Ingredient(wanted));
			anyOffered |= canOffer(client, wanted);
		}

		Row main = new Row(steps, Component.translatable("hud.twoheavens.coming_up"),
				0, false, null, false, null);
		if (!anyOffered) {
			drawRows(graphics, client, List.of(main));
			return 1;
		}
		if (ShiftState.isDown()) {
			drawRows(graphics, client, List.of(main));
			drawRecipeGrids(graphics, client, upcoming);
			return 1;
		}
		drawRows(graphics, client, List.of(main, Row.text(
				Component.translatable("hud.twoheavens.shift_for_recipe"))));
		return 2;
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
		// blockInView anchors at the block's middle with no rise; this one hangs below.
		anchorRise = -KERA_BELOW;

		// cool_stage counts up as it cools, which is already progress toward being
		// breakable - so the bar uses it directly and fills, like every other one.
		float remaining = progress("kera", kera.getValue(KeraBlock.COOL_STAGE), COOL_STAGE_MAX, COOL_STAGE_MS);

		Font font = client.font;
		Component label = Component.translatable("hud.twoheavens.kera_cooling");
		int labelWidth = font.width(label);
		int width = Math.max(labelWidth, BAR_WIDTH);
		int x = (graphics.guiWidth() - width) / 2;
		int y = top(graphics);

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
	private record Ingredient(ItemStack stack, Identifier sprite, boolean afterArrow) {
		Ingredient(ItemStack stack) {
			this(stack, null, false);
		}

		Ingredient(Identifier sprite) {
			this(ItemStack.EMPTY, sprite, false);
		}

		/**
		 * Joined to the step before it with an arrow rather than a plus, for the rare
		 * case where the game enforces an order. "+" is set grammar - it reads as a
		 * list of things to bring, which is right for everything else here, and wrong
		 * for the tatara's fill, where satetsu is refused until the charcoal is full.
		 */
		static Ingredient then(ItemStack stack) {
			return new Ingredient(stack, null, true);
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

		/**
		 * Centred, like every other row. The Shift prompt used to be pinned to the first
		 * icon's x so it sat directly under the item it referred to; centring it keeps
		 * the whole prompt on one axis instead, which reads as one block once a recipe
		 * grid can open underneath it.
		 */
		static Row text(Component text) {
			return new Row(List.of(), text, 0, false, null, false, null);
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
		drawRows(graphics, client, rows, 0);
	}

	/**
	 * @param extraGap additional space between rows. The whole block is lifted by it as
	 *                 well, so the LAST row stays where it would have been and the rows
	 *                 above move up - anything already anchored near the hotbar keeps
	 *                 its place.
	 */
	private static void drawRows(GuiGraphicsExtractor graphics, Minecraft client, List<Row> rows, int extraGap) {
		int rowHeight = MOUSE_H + ROW_GAP + extraGap;
		int top = top(graphics) - extraGap * (rows.size() - 1);
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
			// Pushed out past the icon's bottom-right corner rather than sitting on it -
			// at +2 the digit overlapped the mouse body and was hard to read against it.
			//
			// It cannot go much further. Right runs into the GAP before the first item,
			// and down runs into the next row: rows are MOUSE_H + ROW_GAP apart, so at
			// +4 the digit's baseline lands exactly on that pitch. If it still reads
			// badly the answer is a shadow behind it, not more offset.
			graphics.text(font, count,
					x + MOUSE_W - font.width(count) + COUNT_NUDGE,
					y + MOUSE_H - font.lineHeight + COUNT_NUDGE,
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
				// Anchored to the block's MIDDLE, so the prompt lands over its body -
				// the side, never the opening on top where the smelt is read from.
				anchor = Vec3.atCenterOf(pos);
				anchorRise = 0;
				return state;
			}
		}
		return null;
	}
}
