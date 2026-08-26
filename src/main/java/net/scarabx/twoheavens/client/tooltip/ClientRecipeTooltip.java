package net.scarabx.twoheavens.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.scarabx.twoheavens.item.RecipeTooltipData;

import java.util.List;

/**
 * Draws each recipe as a crafting grid, an arrow, and the result plus its name.
 * Slots are drawn with fill/outline rather than a sprite so this doesn't depend
 * on any particular vanilla GUI atlas entry surviving a version bump.
 */
public class ClientRecipeTooltip implements ClientTooltipComponent {

	// Minecraft's own GUI sprites, at their real sizes, so these read exactly as they
	// do in the furnace and villager screens instead of being redrawn by hand.
	private static final Identifier LIT_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final int LIT_W = 14;
	private static final int LIT_H = 14;
	private static final Identifier ARROW_SPRITE = Identifier.withDefaultNamespace("container/villager/trade_arrow");
	private static final int ARROW_H = 9;

	/** How long each fuel is shown before the next. */
	private static final long FUEL_CYCLE_MS = 1000L;

	/**
	 * Every burnable item in the game, cycled in the furnace's fuel slot the way
	 * recipe mods cycle a tag's members: the slot means "any fuel", and one fixed item
	 * would read as "this fuel".
	 *
	 * Taken from the level's own FuelValues rather than a hand-written list, so it
	 * covers whatever the pack actually allows - datapack additions and other mods'
	 * fuels included. Cached because it cannot change without a reload.
	 */
	private static List<Item> fuels;

	private static List<Item> fuels() {
		if (fuels == null) {
			Level level = Minecraft.getInstance().level;
			if (level == null) {
				return List.of(Items.COAL);
			}
			fuels = List.copyOf(level.fuelValues().fuelItems());
		}
		return fuels;
	}

	private static final int SLOT = 18;
	private static final int ARROW_WIDTH = 10;
	private static final int GAP = 2;
	private static final int ROW_GAP = 4;

	// Vanilla's slot colours: a grey interior with a dark top-left edge and a white
	// bottom-right one, which is what gives Minecraft slots their sunken look.
	private static final int SLOT_INTERIOR = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
	// Same white vanilla uses for a common item's name.
	private static final int NAME_COLOR = 0xFFFFFFFF;

	private final RecipeTooltipData data;

	public ClientRecipeTooltip(RecipeTooltipData data) {
		this.data = data;
	}

	/**
	 * Furnace layout stacks input over fuel, so it is two slots tall plus the gap.
	 *
	 * The gap must be at least LIT_H, or the flame - which is centred in it - hangs
	 * over the slots above and below. At 6 it covered 4px of each.
	 */
	private static final int FURNACE_GAP = LIT_H + 2;

	private static int entryHeight(RecipeTooltipData.Entry entry) {
		if (entry.smelting()) {
			return SLOT * 2 + FURNACE_GAP;
		}
		return Math.max(entry.height() * SLOT, SLOT);
	}

	private static int gridWidth(RecipeTooltipData.Entry entry) {
		return entry.smelting() ? SLOT : entry.width() * SLOT;
	}

	@Override
	public int getHeight(Font font) {
		int total = 0;
		for (RecipeTooltipData.Entry entry : this.data.entries()) {
			total += entryHeight(entry) + ROW_GAP;
		}
		return total;
	}

	@Override
	public int getWidth(Font font) {
		int widest = 0;
		for (RecipeTooltipData.Entry entry : this.data.entries()) {
			int w = gridWidth(entry) + GAP + ARROW_WIDTH + GAP + SLOT
					+ GAP + font.width(entry.result().getHoverName());
			widest = Math.max(widest, w);
		}
		return widest;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		int rowY = y;
		int slotIndex = 0;
		for (RecipeTooltipData.Entry entry : this.data.entries()) {
			int height = entryHeight(entry);

			if (entry.smelting()) {
				// Input on top, empty fuel slot below, flame between - the furnace's
				// own iconography, so this cannot be mistaken for a crafting recipe.
				drawSlot(graphics, x, rowY);
				graphics.item(entry.grid().get(0), x + 1, rowY + 1, slotIndex++);
				graphics.itemDecorations(font, entry.grid().get(0), x + 1, rowY + 1);

				int fuelY = rowY + SLOT + FURNACE_GAP;
				drawSlot(graphics, x, fuelY);
				List<Item> burnable = fuels();
				ItemStack fuel = new ItemStack(burnable.get(
						(int) ((System.currentTimeMillis() / FUEL_CYCLE_MS) % burnable.size())));
				graphics.item(fuel, x + 1, fuelY + 1, slotIndex++);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE,
						x + (SLOT - LIT_W) / 2, rowY + SLOT + (FURNACE_GAP - LIT_H) / 2, LIT_W, LIT_H);
			}

			for (int row = 0; entry.smelting() ? false : row < entry.height(); row++) {
				for (int col = 0; col < entry.width(); col++) {
					int cellX = x + col * SLOT;
					int cellY = rowY + row * SLOT;
					drawSlot(graphics, cellX, cellY);

					ItemStack stack = entry.grid().get(row * entry.width() + col);
					if (!stack.isEmpty()) {
						graphics.item(stack, cellX + 1, cellY + 1, slotIndex++);
						graphics.itemDecorations(font, stack, cellX + 1, cellY + 1);
					}
				}
			}

			int arrowX = x + gridWidth(entry) + GAP;
			int midY = rowY + height / 2;
			drawArrow(graphics, arrowX, midY);

			int resultX = arrowX + ARROW_WIDTH + GAP;
			int resultY = rowY + (height - SLOT) / 2;
			drawSlot(graphics, resultX, resultY);
			graphics.item(entry.result(), resultX + 1, resultY + 1, slotIndex++);
			graphics.itemDecorations(font, entry.result(), resultX + 1, resultY + 1);

			graphics.text(font, entry.result().getHoverName(),
					resultX + SLOT + GAP, midY - font.lineHeight / 2, NAME_COLOR);

			rowY += height + ROW_GAP;
		}
	}

	/** One vanilla-style slot: white base, dark top-left edge, grey interior. */
	private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + SLOT, y + SLOT, SLOT_HIGHLIGHT);
		graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, SLOT_SHADOW);
		graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_INTERIOR);
	}

	private static void drawArrow(GuiGraphicsExtractor graphics, int x, int midY) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_SPRITE,
				x, midY - ARROW_H / 2, ARROW_WIDTH, ARROW_H);
	}
}
