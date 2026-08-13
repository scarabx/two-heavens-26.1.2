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
import net.scarabx.twoheavens.item.custom.DaishoSayaItem;
import net.scarabx.twoheavens.item.custom.DaishoSayaObiItem;
import net.scarabx.twoheavens.item.custom.HammerItem;
import net.scarabx.twoheavens.item.custom.ObiItem;
import net.scarabx.twoheavens.item.custom.TataraClayItem;
import net.scarabx.twoheavens.item.custom.DaishoItem;
import net.scarabx.twoheavens.item.custom.KatanaBladeItem;
import net.scarabx.twoheavens.item.custom.KatanaItem;
import net.scarabx.twoheavens.item.custom.KatanaTsukaItem;
import net.scarabx.twoheavens.item.custom.TsubaItem;
import net.scarabx.twoheavens.item.custom.WakizashiBladeItem;
import net.scarabx.twoheavens.item.custom.WakizashiItem;
import net.scarabx.twoheavens.item.custom.WakizashiTsukaItem;

public class ModItems {

	public static final Item KATANA = registerItem("katana", KatanaItem::new,
			new Item.Properties().sword(ToolMaterial.IRON, 9.0F, -2.4F));
	public static final Item WAKIZASHI = registerItem("wakizashi", WakizashiItem::new,
			new Item.Properties().sword(ToolMaterial.IRON, 4.0F, -2.4F));
	public static final Item DAISHO = registerItem("daisho", DaishoItem::new);
	public static final Item KATANA_TSUKA = registerItem("katana_tsuka", KatanaTsukaItem::new);
	public static final Item KATANA_BLADE = registerItem("katana_blade", KatanaBladeItem::new);
	public static final Item WAKIZASHI_TSUKA = registerItem("wakizashi_tsuka", WakizashiTsukaItem::new);
	public static final Item WAKIZASHI_BLADE = registerItem("wakizashi_blade", WakizashiBladeItem::new);
	public static final Item TSUBA = registerItem("tsuba", TsubaItem::new);
	public static final Item TATARA_CLAY = registerItem("tatara_clay", TataraClayItem::new);
	public static final Item MOLTEN_KERA = registerItem("hot_kera_block", Item::new);
	public static final Item TAMAHAGANE_INGOT = registerItem("tamahagane_ingot", Item::new);
	public static final Item FLAT_TAMAHAGANE_INGOT = registerItem("flat_tamahagane_ingot", Item::new);
	public static final Item HOT_WAKIZASHI_BLADE = registerItem("hot_wakizashi_blade", Item::new);
	public static final Item HOT_KATANA_BLADE = registerItem("hot_katana_blade", Item::new);
	public static final Item TONGS = registerItem("tongs", Item::new,
			new Item.Properties().durability(128));
	public static final Item HAMMER = registerItem("hammer", HammerItem::new,
			new Item.Properties().durability(250));
	public static final Item BELLOWS = registerItem("bellows", BellowsItem::new,
			new Item.Properties().durability(64));
	public static final Item OBI = registerItem("obi", ObiItem::new);
	public static final Item DAISHO_SAYA = registerItem("daisho_saya", DaishoSayaItem::new);
	public static final Item DAISHO_SAYA_OBI = registerItem("daisho_saya_obi", DaishoSayaObiItem::new);

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
