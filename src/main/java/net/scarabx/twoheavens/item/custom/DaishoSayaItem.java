package net.scarabx.twoheavens.item.custom;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.scarabx.twoheavens.item.ItemRecipeTooltip;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.scarabx.twoheavens.item.DisassembleHelper;
import net.scarabx.twoheavens.item.ModItems;

import java.util.function.Consumer;
import net.scarabx.twoheavens.client.render.DaishoSayaRenderer;

import java.util.function.Consumer;

public class DaishoSayaItem extends Item implements GeoItem {

	private final AnimatableInstanceCache animatableInstanceCache = new SingletonAnimatableInstanceCache(this);

	public DaishoSayaItem(Properties properties) {
		super(properties);
	}

	// Disassembly line first, then the recipe prompt underneath it.
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		consumer.accept(Component.translatable("tooltip.twoheavens.take_apart"));
		super.appendHoverText(stack, context, display, consumer, flag);
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return DisassembleHelper.splitInto(level, player, hand, ModItems.KATANA, ModItems.WAKIZASHI);
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
			private DaishoSayaRenderer renderer;

			@Override
			public com.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
				if (this.renderer == null) {
					this.renderer = new DaishoSayaRenderer();
				}
				return this.renderer;
			}
		});
	}
}
