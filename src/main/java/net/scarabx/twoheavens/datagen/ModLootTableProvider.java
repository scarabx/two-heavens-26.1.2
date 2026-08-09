package net.scarabx.twoheavens.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.scarabx.twoheavens.block.ModBlocks;

import java.util.concurrent.CompletableFuture;

public class ModLootTableProvider extends FabricBlockLootSubProvider {

	public ModLootTableProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	@Override
	public void generate() {
		this.dropSelf(ModBlocks.SATETSU_SAND);
		this.dropSelf(ModBlocks.TATARA_CLAY_BLOCK);
		this.dropSelf(ModBlocks.TATARA_FURNACE);
		this.dropSelf(ModBlocks.TATARA_FURNACE_FIRED);
		this.dropSelf(ModBlocks.KERA);
		this.dropSelf(ModBlocks.KERA_BLOCK);
	}
}
