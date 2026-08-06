package net.scarabx.twoheavens.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.block.entity.KeraBlockEntity;
import net.scarabx.twoheavens.block.entity.TataraFurnaceBlockEntity;
import net.scarabx.twoheavens.block.entity.TataraFurnaceFiredBlockEntity;

public class ModBlockEntities {

	public static final BlockEntityType<TataraFurnaceBlockEntity> TATARA_FURNACE = register("tatara_furnace",
			FabricBlockEntityTypeBuilder.create(TataraFurnaceBlockEntity::new, ModBlocks.TATARA_FURNACE).build());

	public static final BlockEntityType<TataraFurnaceFiredBlockEntity> TATARA_FURNACE_FIRED = register("tatara_furnace_fired",
			FabricBlockEntityTypeBuilder.create(TataraFurnaceFiredBlockEntity::new, ModBlocks.TATARA_FURNACE_FIRED).build());

	public static final BlockEntityType<KeraBlockEntity> KERA = register("kera",
			FabricBlockEntityTypeBuilder.create(KeraBlockEntity::new, ModBlocks.KERA_BLOCK).build());

	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> register(String name, BlockEntityType<T> type) {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, name));
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
	}

	public static void registerModBlockEntities() {
		TwoHeavens.LOGGER.info("Registering Mod Block Entities for " + TwoHeavens.MOD_ID);
	}
}
