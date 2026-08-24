package net.scarabx.twoheavens.item.custom;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.scarabx.twoheavens.item.DisassembleHelper;
import net.scarabx.twoheavens.item.ModItems;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.scarabx.twoheavens.client.render.DaishoObiArmorRenderer;
import net.scarabx.twoheavens.client.render.DaishoObiRenderer;

import java.util.function.Consumer;

public class DaishoObiItem extends Item implements GeoItem {

	private final AnimatableInstanceCache animatableInstanceCache = new SingletonAnimatableInstanceCache(this);

	public DaishoObiItem(Properties properties) {
		super(properties);
	}

	// Added before the super call so it sits above the recipe prompt that
	// ItemTooltipMixin appends. Calling super matters: that mixin targets
	// Item#appendHoverText, so skipping it here would drop the prompt entirely.
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		consumer.accept(Component.translatable("tooltip.twoheavens.take_apart")
				.withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, context, display, consumer, flag);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return DisassembleHelper.splitInto(level, player, hand, ModItems.OBI, ModItems.DAISHO_SAYA);
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
			private DaishoObiRenderer itemRenderer;
			private DaishoObiArmorRenderer armorRenderer;

			@Override
			public com.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
				if (this.itemRenderer == null) {
					this.itemRenderer = new DaishoObiRenderer();
				}
				return this.itemRenderer;
			}

			@Override
			public com.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
				if (this.armorRenderer == null) {
					this.armorRenderer = new DaishoObiArmorRenderer();
				}
				return this.armorRenderer;
			}
		});
	}
}
