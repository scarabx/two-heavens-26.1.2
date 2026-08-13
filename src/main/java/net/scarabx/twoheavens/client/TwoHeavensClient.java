package net.scarabx.twoheavens.client;

import eu.pb4.trinkets.api.client.TrinketRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.scarabx.twoheavens.client.render.DaishoSayaObiTrinketRenderer;
import net.scarabx.twoheavens.item.ModItems;

public class TwoHeavensClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TrinketRendererRegistry.registerRenderer(ModItems.DAISHO_SAYA_OBI, new DaishoSayaObiTrinketRenderer());
	}
}
