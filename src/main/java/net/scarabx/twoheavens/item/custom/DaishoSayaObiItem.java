package net.scarabx.twoheavens.item.custom;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.client.render.DaishoSayaObiArmorRenderer;
import net.scarabx.twoheavens.client.render.DaishoSayaObiRenderer;

import java.util.function.Consumer;

public class DaishoSayaObiItem extends Item implements GeoItem {

	private final AnimatableInstanceCache animatableInstanceCache = new SingletonAnimatableInstanceCache(this);

	public DaishoSayaObiItem(Properties properties) {
		super(properties);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.animatableInstanceCache;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private DaishoSayaObiRenderer itemRenderer;
			private DaishoSayaObiArmorRenderer armorRenderer;

			@Override
			public com.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
				if (this.itemRenderer == null) {
					this.itemRenderer = new DaishoSayaObiRenderer();
				}
				return this.itemRenderer;
			}

			@Override
			public com.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
				if (this.armorRenderer == null) {
					this.armorRenderer = new DaishoSayaObiArmorRenderer();
				}
				return this.armorRenderer;
			}
		});
	}
}
