package net.scarabx.twoheavens.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.ModBlockItem;
import net.scarabx.twoheavens.block.custom.KeraBlock;
import net.scarabx.twoheavens.block.custom.KeraStaticBlock;
import net.scarabx.twoheavens.block.custom.SatetsuSandBlock;
import net.scarabx.twoheavens.block.custom.SmithingAnvilBlock;
import net.scarabx.twoheavens.block.custom.TataraClayBlock;
import net.scarabx.twoheavens.block.custom.TataraFurnaceBlock;
import net.scarabx.twoheavens.block.custom.TataraFurnaceFiredBlock;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

public class ModBlocks {

	public static final Block SATETSU_SAND = registerBlockWithItem("satetsu_sand", SatetsuSandBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND));

	public static final Block TATARA_CLAY_BLOCK = registerBlockWithItem("tatara_clay_block", TataraClayBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(1.25F, 4.5F).sound(SoundType.STONE));

	public static final Block TATARA_FURNACE = registerBlockWithItem("tatara_furnace", TataraFurnaceBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(1.25F, 4.5F).sound(SoundType.STONE));

	public static final Block TATARA_FURNACE_FIRED = registerBlockWithItem("tatara_furnace_fired", TataraFurnaceFiredBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F).sound(SoundType.STONE)
					.requiresCorrectToolForDrops());

	public static final Block KERA = registerBlockWithItem("kera", KeraStaticBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());

	// Unbreakable while it cools, on purpose: the wait IS the step, mirroring the real
	// process where bloom is left to cool before being broken up and reheated. That
	// only works because the HUD says so - an unbreakable block with no explanation
	// reads as a bug.
	public static final Block KERA_BLOCK = registerBlockWithItem("kera_block", KeraBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion());

	public static final Block SMITHING_ANVIL = registerBlockWithItem("smithing_anvil", SmithingAnvilBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
					.strength(5.0F, 1200.0F).sound(SoundType.ANVIL).pushReaction(PushReaction.BLOCK));

	private static Block registerBlockWithItem(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties blockProperties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, blockFactory.apply(blockProperties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new ModBlockItem(block, new Item.Properties().setId(itemKey)));

		return block;
	}

	public static void registerModBlocks() {
		TwoHeavens.LOGGER.info("Registering Mod Blocks for " + TwoHeavens.MOD_ID);
	}
}
