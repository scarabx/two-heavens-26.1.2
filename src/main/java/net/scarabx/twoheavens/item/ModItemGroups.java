package net.scarabx.twoheavens.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.block.ModBlocks;

public class ModItemGroups {

	public static final ResourceKey<CreativeModeTab> TWO_HEAVENS = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, "two_heavens"));

	public static void registerItemGroups() {
		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				TWO_HEAVENS,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.twoheavens.two_heavens"))
						.icon(() -> new ItemStack(ModItems.DAISHO))
						.displayItems((parameters, entries) -> {
							entries.accept(ModItems.KATANA);
							entries.accept(ModItems.WAKIZASHI);
							entries.accept(ModItems.DAISHO);
							entries.accept(ModItems.KATANA_HILT);
							entries.accept(ModItems.KATANA_BLADE);
							entries.accept(ModItems.WAKIZASHI_HILT);
							entries.accept(ModItems.WAKIZASHI_BLADE);
							entries.accept(ModItems.TATARA_CLAY);
							entries.accept(ModBlocks.SATETSU_SAND);
							entries.accept(ModBlocks.TATARA_CLAY_BLOCK);
							entries.accept(ModBlocks.TATARA_FURNACE);
							entries.accept(ModBlocks.TATARA_FURNACE_FIRED);
							entries.accept(ModBlocks.KERA_BLOCK);
							entries.accept(ModItems.HAMMER);
							entries.accept(ModItems.BELLOWS);
						})
						.build());
	}
}
