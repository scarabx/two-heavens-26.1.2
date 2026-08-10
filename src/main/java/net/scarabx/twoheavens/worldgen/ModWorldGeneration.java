package net.scarabx.twoheavens.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.scarabx.twoheavens.TwoHeavens;

public class ModWorldGeneration {

	public static final ResourceKey<PlacedFeature> SATETSU_SAND_PATCH = ResourceKey.create(
			net.minecraft.core.registries.Registries.PLACED_FEATURE,
			Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, "satetsu_sand_patch"));

	public static void registerWorldGeneration() {
		TwoHeavens.LOGGER.info("Registering World Generation for " + TwoHeavens.MOD_ID);

		// Any overworld biome, including oceans - the block_predicate_filter on the
		// placed feature restricts actual placement to spots touching water (shoreline
		// only, never fully submerged), so this covers rivers, beaches, ocean coasts,
		// and lakes/ponds anywhere that condition is met.
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.LOCAL_MODIFICATIONS,
				SATETSU_SAND_PATCH);
	}
}
