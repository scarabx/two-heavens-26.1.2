package net.scarabx.twoheavens.datagen;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.item.ModItems;

public class ModModelProvider extends FabricModelProvider {

	public ModModelProvider(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators blockModels) {
		// Simple, single-texture cube blocks: generates the blockstate,
		// the block model, and the block-item model automatically.
		blockModels.createTrivialCube(ModBlocks.SATETSU_SAND);
		blockModels.registerSimpleItemModel(ModBlocks.SATETSU_SAND, ModelLocationUtils.getModelLocation(ModBlocks.SATETSU_SAND));

		blockModels.createTrivialCube(ModBlocks.TATARA_CLAY_BLOCK);
		blockModels.registerSimpleItemModel(ModBlocks.TATARA_CLAY_BLOCK, ModelLocationUtils.getModelLocation(ModBlocks.TATARA_CLAY_BLOCK));

		// Tatara Furnace blocks use a custom composter-style hollow-cube shape
		// and stay hand-authored (see models/block/tatara_furnace*.json).
	}

	@Override
	public void generateItemModels(ItemModelGenerators itemModels) {
		// Simple flat 2D item icons.
		itemModels.generateFlatItem(ModItems.TATARA_CLAY, ModelTemplates.FLAT_ITEM);
		itemModels.generateFlatItem(ModItems.HAMMER, ModelTemplates.FLAT_HANDHELD_ITEM);
		itemModels.generateFlatItem(ModItems.BELLOWS, ModelTemplates.FLAT_HANDHELD_ITEM);
		itemModels.generateFlatItem(ModItems.TONGS, ModelTemplates.FLAT_HANDHELD_ITEM);

		// The katana/wakizashi/daisho/hilt/blade weapons use custom 3D
		// Blockbench models and stay hand-authored.
	}
}
