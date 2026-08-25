package net.scarabx.twoheavens.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.item.ItemRecipeTooltip;
import java.util.Optional;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import net.minecraft.world.item.Item;
import net.scarabx.twoheavens.client.render.ObiRenderer;

import java.util.function.Consumer;

public class ObiItem extends Item implements GeoItem {

	private final AnimatableInstanceCache animatableInstanceCache = new SingletonAnimatableInstanceCache(this);

	public ObiItem(Properties properties) {
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
			private ObiRenderer renderer;

			@Override
			public com.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
				if (this.renderer == null) {
					this.renderer = new ObiRenderer();
				}
				return this.renderer;
			}
		});
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}
}
