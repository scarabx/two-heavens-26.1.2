package net.scarabx.twoheavens.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import eu.pb4.trinkets.api.client.TrinketRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.client.animation.PlayerHandAnimator;
import net.scarabx.twoheavens.client.animation.SwordDrawController;
import net.scarabx.twoheavens.client.animation.TwoHeavensPlayerAnimation;
import net.scarabx.twoheavens.client.render.DaishoSayaObiTrinketRenderer;
import net.scarabx.twoheavens.item.ModItems;

public class TwoHeavensClient implements ClientModInitializer {

	// Temporary: proves the player-animation foundation actually moves both
	// arms (first and third person) before any real combat animation exists.
	// Not a real game feature - remove once the sword-draw work replaces it.
	private static final KeyMapping TEST_WAVE_KEY = new KeyMapping(
			"key.twoheavens.test_wave", InputConstants.Type.KEYSYM, InputConstants.KEY_J, KeyMapping.Category.MISC);

	private static final KeyMapping DRAW_SWORDS_KEY = new KeyMapping(
			"key.twoheavens.draw_swords", InputConstants.Type.KEYSYM, InputConstants.KEY_R, KeyMapping.Category.MISC);

	@Override
	public void onInitializeClient() {
		TrinketRendererRegistry.registerRenderer(ModItems.DAISHO_SAYA_OBI, new DaishoSayaObiTrinketRenderer());

		TwoHeavensPlayerAnimation.register();

		// Lets F3+T pick up edited animation JSON without a full game
		// restart - our animations are cached in memory the first time
		// they're played (see TwoHeavensPlayerAnimation), so a plain
		// resource reload alone wouldn't refresh them otherwise.
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return TwoHeavens.id("player_animation_cache_clear");
					}

					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						TwoHeavensPlayerAnimation.clearCache();
					}
				});

		KeyMappingHelper.registerKeyMapping(TEST_WAVE_KEY);
		KeyMappingHelper.registerKeyMapping(DRAW_SWORDS_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TEST_WAVE_KEY.consumeClick()) {
				if (client.player != null) {
					PlayerHandAnimator.trigger(client.player,
							RawAnimation.begin().thenPlay(TwoHeavensPlayerAnimation.getTestWaveAnimation()));
				}
			}
			while (DRAW_SWORDS_KEY.consumeClick()) {
				if (client.player != null) {
					SwordDrawController.toggle(client.player);
				}
			}
			SwordDrawController.tick(client);
		});
	}
}
