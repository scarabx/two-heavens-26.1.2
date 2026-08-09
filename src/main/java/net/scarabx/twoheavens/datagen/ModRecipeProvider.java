package net.scarabx.twoheavens.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.item.ModItems;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {

	public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	@Override
	public String getName() {
		return "Two Heavens/Recipes";
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
		return new RecipeProvider(registries, output) {
			private final HolderGetter<net.minecraft.world.item.Item> items = registries.lookupOrThrow(Registries.ITEM);

			@Override
			public void buildRecipes() {
				ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.MISC, ModItems.TATARA_CLAY)
						.requires(Items.SUGAR_CANE)
						.requires(Items.CHARCOAL)
						.requires(Items.CLAY_BALL)
						.unlockedBy("has_clay_ball", this.has(Items.CLAY_BALL))
						.save(output);

				ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.MISC, ModBlocks.TATARA_CLAY_BLOCK)
						.requires(ModItems.TATARA_CLAY, 4)
						.unlockedBy("has_tatara_clay", this.has(ModItems.TATARA_CLAY))
						.save(output);

				ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.MISC, ModBlocks.TATARA_FURNACE)
						.requires(ModBlocks.TATARA_CLAY_BLOCK, 8)
						.unlockedBy("has_tatara_clay_block", this.has(ModBlocks.TATARA_CLAY_BLOCK))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.TOOLS, ModItems.HAMMER)
						.pattern("SSS")
						.pattern("SSS")
						.pattern(" T ")
						.define('S', ItemTags.STONE_TOOL_MATERIALS)
						.define('T', Items.STICK)
						.unlockedBy("has_stone", this.has(ItemTags.STONE_TOOL_MATERIALS))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.TOOLS, ModItems.BELLOWS)
						.pattern("PPP")
						.pattern("ILL")
						.pattern("PPP")
						.define('P', ItemTags.PLANKS)
						.define('I', Items.IRON_INGOT)
						.define('L', Items.LEATHER)
						.unlockedBy("has_leather", this.has(Items.LEATHER))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.COMBAT, ModItems.KATANA)
						.pattern(" b ")
						.pattern(" g ")
						.pattern(" h ")
						.define('b', ModItems.KATANA_BLADE)
						.define('g', ModItems.TSUBA)
						.define('h', ModItems.KATANA_TSUKA)
						.unlockedBy("has_katana_blade", this.has(ModItems.KATANA_BLADE))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.COMBAT, ModItems.WAKIZASHI)
						.pattern(" b ")
						.pattern(" g ")
						.pattern(" h ")
						.define('b', ModItems.WAKIZASHI_BLADE)
						.define('g', ModItems.TSUBA)
						.define('h', ModItems.WAKIZASHI_TSUKA)
						.unlockedBy("has_wakizashi_blade", this.has(ModItems.WAKIZASHI_BLADE))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.COMBAT, ModItems.KATANA_TSUKA)
						.pattern("LSL")
						.pattern("LSL")
						.pattern("LSL")
						.define('L', Items.LEATHER)
						.define('S', Items.STICK)
						.unlockedBy("has_leather", this.has(Items.LEATHER))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.COMBAT, ModItems.WAKIZASHI_TSUKA)
						.pattern("LSL")
						.pattern("LSL")
						.define('L', Items.LEATHER)
						.define('S', Items.STICK)
						.unlockedBy("has_leather", this.has(Items.LEATHER))
						.save(output);

				ShapedRecipeBuilder.shaped(this.items, RecipeCategory.COMBAT, ModItems.TSUBA)
						.pattern(" I ")
						.pattern("I I")
						.pattern(" I ")
						.define('I', Items.IRON_INGOT)
						.unlockedBy("has_iron_ingot", this.has(Items.IRON_INGOT))
						.save(output);
			}
		};
	}
}
