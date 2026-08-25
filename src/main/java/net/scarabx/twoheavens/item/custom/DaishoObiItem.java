package net.scarabx.twoheavens.item.custom;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import net.minecraft.world.entity.EquipmentSlot;
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

	// Ordered by when a player needs each line: equipping blocks everything, drawing
	// is the point of the item, and taking it apart is the only one you could not
	// possibly need on first contact. Never an ingredient, so no recipe prompt here.
	//
	// Trinkets adds its own "Equippable in the Belt trinket slot" line, so these
	// deliberately say what it cannot: how to reach that slot, and the key to use.
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		consumer.accept(Component.translatable("tooltip.twoheavens.obi_equip"));
		consumer.accept(Component.translatable("tooltip.twoheavens.obi_draw"));
		consumer.accept(Component.translatable("tooltip.twoheavens.take_apart"));
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
