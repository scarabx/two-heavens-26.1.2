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
						// The Daisho is the tab's emblem only - a katana and wakizashi paired
						// says what the mod is better than either sword alone. It is NOT listed
						// below: it has no recipe and no function since combos replaced it, so
						// offering it in the grid would hand players a dead item. Registered
						// purely so this ItemStack can exist.
						.icon(() -> new ItemStack(ModItems.DAISHO))
						.displayItems((parameters, entries) -> {
							entries.accept(ModItems.KATANA);
							entries.accept(ModItems.WAKIZASHI);
							entries.accept(ModItems.KATANA_TSUKA);
							entries.accept(ModItems.KATANA_BLADE);
							entries.accept(ModItems.WAKIZASHI_TSUKA);
							entries.accept(ModItems.WAKIZASHI_BLADE);
							entries.accept(ModItems.TSUBA);
							entries.accept(ModItems.OBI);
							entries.accept(ModItems.DAISHO_SAYA);
							entries.accept(ModItems.DAISHO_OBI);
							entries.accept(ModItems.TATARA_CLAY);
							entries.accept(ModBlocks.SATETSU_SAND);
							entries.accept(ModBlocks.TATARA_CLAY_BLOCK);
							entries.accept(ModBlocks.TATARA_FURNACE);
							entries.accept(ModBlocks.TATARA_FURNACE_FIRED);
							entries.accept(ModBlocks.KERA);
							entries.accept(ModBlocks.KERA_BLOCK);
							entries.accept(ModBlocks.SMITHING_ANVIL);
							entries.accept(ModItems.MOLTEN_KERA);
							entries.accept(ModItems.HAMMER);
							entries.accept(ModItems.BELLOWS);
							entries.accept(ModItems.TONGS);
						})
						.build());
	}
}
