package net.scarabx.twoheavens.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.custom.BellowsItem;
import net.scarabx.twoheavens.item.custom.HammerItem;
import net.scarabx.twoheavens.item.custom.TataraClayItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.DaishoItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.KatanaBladeItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.KatanaHiltItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.KatanaItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.WakizashiBladeItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.WakizashiHiltItem;
import net.scarabx.twoheavens.item.custom.japanese_weapons.WakizashiItem;

public class ModItems {

	public static final Item KATANA = registerItem("katana", KatanaItem::new,
			new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.4F));
	public static final Item WAKIZASHI = registerItem("wakizashi", WakizashiItem::new,
			new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.4F));
	public static final Item DAISHO = registerItem("daisho", DaishoItem::new);
	public static final Item KATANA_HILT = registerItem("katana_hilt", KatanaHiltItem::new);
	public static final Item KATANA_BLADE = registerItem("katana_blade", KatanaBladeItem::new);
	public static final Item WAKIZASHI_HILT = registerItem("wakizashi_hilt", WakizashiHiltItem::new);
	public static final Item WAKIZASHI_BLADE = registerItem("wakizashi_blade", WakizashiBladeItem::new);
	public static final Item TATARA_CLAY = registerItem("tatara_clay", TataraClayItem::new);
	public static final Item HAMMER = registerItem("hammer", HammerItem::new,
			new Item.Properties().durability(250));
	public static final Item BELLOWS = registerItem("bellows", BellowsItem::new,
			new Item.Properties().durability(64));

	private static Item registerItem(String name, java.util.function.Function<Item.Properties, Item> itemFactory) {
		return registerItem(name, itemFactory, new Item.Properties());
	}

	private static Item registerItem(String name, java.util.function.Function<Item.Properties, Item> itemFactory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TwoHeavens.MOD_ID, name));
		Item item = itemFactory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void registerModItems() {
		TwoHeavens.LOGGER.info("Registering Mod Items for " + TwoHeavens.MOD_ID);
	}
}
