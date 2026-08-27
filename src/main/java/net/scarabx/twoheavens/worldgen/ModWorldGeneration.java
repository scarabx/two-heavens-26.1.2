package net.scarabx.twoheavens.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.scarabx.twoheavens.TwoHeavens;

public class ModWorldGeneration {

	public static final ResourceKey<PlacedFeature> SATETSU_SAND_PATCH = ResourceKey.create(
			net.minecraft.core.registries.Registries.PLACED_FEATURE,
			Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, "satetsu_sand_patch"));

	/** Same feature and filters, far more attempts - see registerWorldGeneration. */
	public static final ResourceKey<PlacedFeature> SATETSU_SAND_PATCH_RIVER = ResourceKey.create(
			net.minecraft.core.registries.Registries.PLACED_FEATURE,
			Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, "satetsu_sand_patch_river"));

	public static void registerWorldGeneration() {
		TwoHeavens.LOGGER.info("Registering World Generation for " + TwoHeavens.MOD_ID);

		// Split in two because one shared count cannot suit both cases. A river bank is
		// a thin strip; an ocean beach is a wide expanse. Raising the count to get
		// enough satetsu on banks previously raised it on beaches by the same
		// proportion, and beaches were already generous - so the numbers are set per
		// case instead.
		//
		// The block_predicate_filter on each placed feature still restricts placement
		// to spots touching water, so both remain shoreline-only rather than filling
		// the biome.
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld().and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
				GenerationStep.Decoration.LOCAL_MODIFICATIONS,
				SATETSU_SAND_PATCH);

		BiomeModifications.addFeature(
				BiomeSelectors.tag(BiomeTags.IS_RIVER),
				GenerationStep.Decoration.LOCAL_MODIFICATIONS,
				SATETSU_SAND_PATCH_RIVER);
	}
}
