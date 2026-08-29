package net.scarabx.twoheavens.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.block.ModDataComponents;
import net.scarabx.twoheavens.block.custom.TataraFurnaceBlock;
import net.scarabx.twoheavens.block.custom.TataraFurnaceFiredBlock;

import java.util.concurrent.CompletableFuture;

public class ModLootTableProvider extends FabricBlockLootSubProvider {

	public ModLootTableProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	@Override
	public void generate() {
		this.dropSelf(ModBlocks.SATETSU_SAND);
		this.dropSelf(ModBlocks.TATARA_CLAY_BLOCK);
		// Both furnaces keep what is inside them when broken. Everything the player
		// put in - charcoal, satetsu, whether it is lit, how far along it looks - lives
		// in the block state, so the dropped item carries a BLOCK_STATE component and
		// vanilla's BlockItem re-applies it on placement.
		//
		// The tick counter and heat come along too, via FURNACE_PROGRESS - they cannot
		// live in the block state (a property would need 1200 values), so the block
		// entity exposes them as a component and copy_components carries it. Losing
		// 50 seconds of a burn to a misplaced pickaxe was harsher than letting someone
		// pause one.
		this.add(ModBlocks.TATARA_FURNACE, block -> this.createSingleItemTable(block)
				.apply(CopyBlockState.copyState(block)
						.copy(TataraFurnaceBlock.LIT)
						.copy(TataraFurnaceBlock.CHARCOAL_LEVEL)
						.copy(TataraFurnaceBlock.BURN_STAGE)
						.copy(TataraFurnaceBlock.COLOR_STAGE))
				.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
						.include(ModDataComponents.FURNACE_PROGRESS)));

		this.add(ModBlocks.TATARA_FURNACE_FIRED, block -> this.createSingleItemTable(block)
				.apply(CopyBlockState.copyState(block)
						.copy(TataraFurnaceFiredBlock.LIT)
						.copy(TataraFurnaceFiredBlock.CHARCOAL_LEVEL)
						.copy(TataraFurnaceFiredBlock.SATETSU_LEVEL)
						.copy(TataraFurnaceFiredBlock.SMELT_STAGE)
						.copy(TataraFurnaceFiredBlock.REDNESS_STAGE)
						.copy(TataraFurnaceFiredBlock.KERA_FORMED)
						.copy(TataraFurnaceFiredBlock.CRACK_STAGE)
						// Every declared property must be copied or breaking the block
						// silently resets that one. This was the odd one out: seven of
						// eight survived, so a furnace broken mid-bellows-phase came back
						// with its pump count at zero while everything else was intact.
						.copy(TataraFurnaceFiredBlock.BELLOWS_PROGRESS))
				.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
						.include(ModDataComponents.FURNACE_PROGRESS)));
		this.dropSelf(ModBlocks.KERA);
		this.dropSelf(ModBlocks.KERA_BLOCK);
		this.dropSelf(ModBlocks.SMITHING_ANVIL);
	}
}
