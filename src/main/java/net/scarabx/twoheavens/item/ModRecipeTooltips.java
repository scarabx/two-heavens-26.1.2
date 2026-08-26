package net.scarabx.twoheavens.item;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.scarabx.twoheavens.block.ModBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recipe lookup for tooltips, in both directions: what an item is MADE FROM, and
 * what it is USED IN.
 *
 * The backward direction matters more than it looks. The forward one answers "what
 * can I do with this?", but a stuck player's question is the opposite - the furnace
 * prompt tells them they need a Bellows and nothing tells them how to get one, and a
 * HUD icon cannot be hovered.
 *
 * MUST be kept in step with ModRecipeProvider by hand. Recipes live server-side
 * in modern versions, so a client tooltip cannot read the real recipe manager;
 * this is a deliberate second copy rather than something derived at runtime.
 */
public final class ModRecipeTooltips {

	/**
	 * Item references only - NOT ItemStacks. Building a stack needs the item's data
	 * components bound, which has not happened yet during mod init; doing it eagerly
	 * throws "Components not bound yet". Stacks are created on demand instead.
	 */
	private record RecipeDef(Item[] cells, int width, int height, Item result, int resultCount, boolean smelting) {
	}

	private static final int GRID_SIZE = 3;

	private static final Map<Item, List<RecipeDef>> USED_IN = new HashMap<>();

	/** Keyed by result: how to make this item. */
	private static final Map<Item, List<RecipeDef>> MADE_FROM = new HashMap<>();

	/**
	 * Recipes whose ingredient is a tag rather than one item - wool, for instance,
	 * where any of the sixteen colours works. Matched by tag at lookup time so every
	 * member shows the recipe without listing them all here.
	 */
	private static final List<TagEntry> USED_IN_TAG = new ArrayList<>();

	private record TagEntry(TagKey<Item> tag, RecipeDef def) {
	}

	private ModRecipeTooltips() {
	}

	public static void register() {
		// 2x2 of Tatara Clay -> Tatara Clay Block
		shaped(new Item[]{
				ModItems.TATARA_CLAY, ModItems.TATARA_CLAY,
				ModItems.TATARA_CLAY, ModItems.TATARA_CLAY
		}, 2, 2, ModBlocks.TATARA_CLAY_BLOCK.asItem(), 1);

		// hollow 3x3 ring of Tatara Clay Block -> Tatara Furnace
		Item clayBlock = ModBlocks.TATARA_CLAY_BLOCK.asItem();
		shaped(new Item[]{
				clayBlock, clayBlock, clayBlock,
				clayBlock, null, clayBlock,
				clayBlock, clayBlock, clayBlock
		}, 3, 3, ModBlocks.TATARA_FURNACE.asItem(), 1);

		// blade / tsuba / tsuka columns
		shaped(new Item[]{
				ModItems.KATANA_BLADE,
				ModItems.TSUBA,
				ModItems.KATANA_TSUKA
		}, 1, 3, ModItems.KATANA, 1);

		shaped(new Item[]{
				ModItems.WAKIZASHI_BLADE,
				ModItems.TSUBA,
				ModItems.WAKIZASHI_TSUKA
		}, 1, 3, ModItems.WAKIZASHI, 1);

		// shapeless pairs, laid out side by side so they still read as a grid
		shaped(new Item[]{ModItems.KATANA, ModItems.WAKIZASHI}, 2, 1, ModItems.DAISHO_SAYA, 1);
		shaped(new Item[]{ModItems.OBI, ModItems.DAISHO_SAYA}, 2, 1, ModItems.DAISHO_OBI, 1);

		// Kera reheats in a real furnace - drawn as one, so it never reads as craftable
		smelting(ModBlocks.KERA.asItem(), ModItems.MOLTEN_KERA);

		// Wool -> Obi. Authored as a 3x3 with empty top and bottom rows, which
		// Minecraft trims, so a single row is the same recipe and reads better.
		// White wool only - the obi is no longer craftable from any dyed wool, so the
		// tooltip must not offer the recipe on the other fifteen colours.
		shaped(new Item[]{
				Items.WHITE_WOOL, Items.WHITE_WOOL, Items.WHITE_WOOL
		}, 3, 1, ModItems.OBI, 1);

		// Leather and sticks make both hilts
		shaped(new Item[]{
				Items.LEATHER, Items.STICK, Items.LEATHER,
				Items.LEATHER, Items.STICK, Items.LEATHER,
				Items.LEATHER, Items.STICK, Items.LEATHER
		}, 3, 3, ModItems.KATANA_TSUKA, 1);

		shaped(new Item[]{
				Items.LEATHER, Items.STICK, Items.LEATHER,
				Items.LEATHER, Items.STICK, Items.LEATHER
		}, 3, 2, ModItems.WAKIZASHI_TSUKA, 1);

		// Iron: tsuba guard, tongs, and part of the bellows and the anvil
		shaped(new Item[]{Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT},
				3, 1, ModItems.TSUBA, 1);

		shaped(new Item[]{Items.IRON_INGOT, Items.IRON_INGOT},
				1, 2, ModItems.TONGS, 1);

		shaped(new Item[]{
				Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
				null, Items.IRON_INGOT, null,
				Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE
		}, 3, 3, ModBlocks.SMITHING_ANVIL.asItem(), 1);

		// Bellows: planks shell, iron nozzle, leather bag
		taggedGrid(ItemTags.PLANKS, Items.OAK_PLANKS, new Item[]{
				null, null, null,
				Items.IRON_INGOT, Items.LEATHER, Items.LEATHER,
				null, null, null
		}, 3, 3, ModItems.BELLOWS, 1);

		// Hammer: stone tool materials over a stick
		taggedGrid(ItemTags.STONE_TOOL_MATERIALS, Items.COBBLESTONE, new Item[]{
				null, null, null,
				null, null, null,
				null, Items.STICK, null
		}, 3, 3, ModItems.HAMMER, 1);

		// Sugar Cane + Charcoal + Clay Ball -> 4 Tatara Clay
		shaped(new Item[]{Items.SUGAR_CANE, Items.CHARCOAL, Items.CLAY_BALL},
				3, 1, ModItems.TATARA_CLAY, 4);
	}

	/** A furnace recipe: one input, smelted into the result. */
	private static void smelting(Item input, Item result) {
		RecipeDef def = new RecipeDef(new Item[]{input}, 1, 1, result, 1, true);
		USED_IN.computeIfAbsent(input, key -> new ArrayList<>()).add(def);
		record(MADE_FROM, result, def);
	}

	/** A single row of one tagged ingredient, e.g. any wool. */
	private static void taggedRow(TagKey<Item> tag, Item icon, int count, Item result, int resultCount) {
		Item[] cells = new Item[count];
		java.util.Arrays.fill(cells, icon);
		RecipeDef def = new RecipeDef(cells, count, 1, result, resultCount, false);
		USED_IN_TAG.add(new TagEntry(tag, def));
		record(MADE_FROM, result, def);
	}

	/**
	 * A recipe mixing a tagged ingredient with plain ones. Null cells are filled with
	 * the tag's icon, so the caller only spells out the non-tag positions.
	 */
	private static void taggedGrid(TagKey<Item> tag, Item icon, Item[] cells,
									int width, int height, Item result, int resultCount) {
		Item[] filled = new Item[cells.length];
		for (int i = 0; i < cells.length; i++) {
			filled[i] = cells[i] == null ? icon : cells[i];
		}
		RecipeDef def = new RecipeDef(filled, width, height, result, resultCount, false);
		USED_IN_TAG.add(new TagEntry(tag, def));
		record(MADE_FROM, result, def);
		// Also register the plain ingredients so hovering those finds it too.
		for (Item cell : cells) {
			if (cell != null) {
				USED_IN.computeIfAbsent(cell, key -> new ArrayList<>()).add(def);
			}
		}
	}

	/** Registers one recipe against every ingredient it uses. */
	private static void shaped(Item[] cells, int width, int height, Item result, int resultCount) {
		RecipeDef def = new RecipeDef(cells, width, height, result, resultCount, false);
		record(MADE_FROM, result, def);
		for (Item cell : cells) {
			if (cell == null) {
				continue;
			}
			List<RecipeDef> forItem = USED_IN.computeIfAbsent(cell, key -> new ArrayList<>());
			if (!forItem.contains(def)) {
				forItem.add(def);
			}
		}
	}

	/**
	 * What this item is made of, as one stack per distinct ingredient with its count.
	 *
	 * For the HUD rather than tooltips: a prompt can name a tool the player does not
	 * own, and hovering cannot help someone holding nothing. This lets the prompt show
	 * the ingredients instead, at the moment they are needed.
	 *
	 * Empty when the item has no recipe - the kera and the blades come out of a
	 * furnace and an anvil, not a crafting grid.
	 */
	public static List<ItemStack> ingredientsFor(Item result) {
		List<RecipeDef> defs = MADE_FROM.get(result);
		if (defs == null || defs.isEmpty()) {
			return List.of();
		}
		RecipeDef def = defs.get(0);
		Map<Item, Integer> counts = new LinkedHashMap<>();
		for (Item cell : def.cells()) {
			if (cell != null) {
				counts.merge(cell, 1, Integer::sum);
			}
		}
		List<ItemStack> parts = new ArrayList<>(counts.size());
		for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
			parts.add(new ItemStack(entry.getKey(), entry.getValue()));
		}
		return parts;
	}

	private static void record(Map<Item, List<RecipeDef>> into, Item key, RecipeDef def) {
		List<RecipeDef> defs = into.computeIfAbsent(key, k -> new ArrayList<>());
		if (!defs.contains(def)) {
			defs.add(def);
		}
	}

	/**
	 * Null when this item neither has a recipe nor appears in one.
	 *
	 * How-to-make comes first, because that is the question someone holding an
	 * unfamiliar item is asking. What-it-makes follows, so an item in the middle of
	 * the chain shows both without needing a way to page between them.
	 */
	public static RecipeTooltipData forIngredient(Item item) {
		List<RecipeDef> defs = new ArrayList<>();
		List<RecipeDef> madeFrom = MADE_FROM.get(item);
		if (madeFrom != null) {
			defs.addAll(madeFrom);
		}
		List<RecipeDef> direct = USED_IN.get(item);
		if (direct != null) {
			for (RecipeDef def : direct) {
				if (!defs.contains(def)) {
					defs.add(def);
				}
			}
		}
		for (TagEntry tagged : USED_IN_TAG) {
			if (item.builtInRegistryHolder().is(tagged.tag()) && !defs.contains(tagged.def())) {
				defs.add(tagged.def());
			}
		}
		if (defs.isEmpty()) {
			return null;
		}
		List<RecipeTooltipData.Entry> entries = new ArrayList<>(defs.size());
		for (RecipeDef def : defs) {
			if (def.smelting()) {
				entries.add(new RecipeTooltipData.Entry(
						List.of(new ItemStack(def.cells()[0])), 1, 1,
						new ItemStack(def.result(), def.resultCount()), true));
			} else {
				entries.add(new RecipeTooltipData.Entry(
						padToFullGrid(def), GRID_SIZE, GRID_SIZE,
						new ItemStack(def.result(), def.resultCount()), false));
			}
		}
		return new RecipeTooltipData(List.copyOf(entries));
	}

	/**
	 * Lays a recipe out on a full 3x3 so every tooltip shows a whole crafting grid,
	 * empty slots included, rather than a bare strip of the used cells. Smaller
	 * patterns are centred, which puts a single row in the middle row and leaves a
	 * 2x2 in the corner, matching how you would actually lay them out.
	 */
	private static List<ItemStack> padToFullGrid(RecipeDef def) {
		List<ItemStack> grid = new ArrayList<>(GRID_SIZE * GRID_SIZE);
		for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
			grid.add(ItemStack.EMPTY);
		}
		int offsetX = (GRID_SIZE - def.width()) / 2;
		int offsetY = (GRID_SIZE - def.height()) / 2;
		for (int row = 0; row < def.height(); row++) {
			for (int col = 0; col < def.width(); col++) {
				Item cell = def.cells()[row * def.width() + col];
				if (cell != null) {
					grid.set((row + offsetY) * GRID_SIZE + (col + offsetX), new ItemStack(cell));
				}
			}
		}
		return List.copyOf(grid);
	}
}
