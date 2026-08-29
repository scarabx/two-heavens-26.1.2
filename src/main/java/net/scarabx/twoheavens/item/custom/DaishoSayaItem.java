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
import net.scarabx.twoheavens.item.ModRecipeTooltips;
import net.scarabx.twoheavens.item.ShiftState;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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

	/**
	 * The whole belt step: this saya's own recipe, the Daisho Obi it combines into,
	 * and how to make the Obi that grid names.
	 *
	 * The Obi is the reason. The Daisho Obi grid demands one at the exact moment the
	 * player has just finished both swords, and **you cannot hover an item you do not
	 * own** - so the tooltip named the last thing standing between them and dual
	 * wielding, and gave them no way to find out it is three wool. Same gap the blades
	 * had with the tsuba and tsuka, same fix.
	 *
	 * Three grids, which ClientRecipeTooltip lays out in two columns on its own past
	 * STACK_LIMIT - the crowding is handled where every other crowded tooltip handles
	 * it, so nothing is decided here.
	 */
	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		if (!ShiftState.isDown()) {
			return Optional.empty();
		}
		return Optional.ofNullable(ModRecipeTooltips.wholeStep(stack.getItem()));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return DisassembleHelper.splitInto(level, player, hand, ModItems.KATANA, ModItems.WAKIZASHI);
	}

	/**
	 * Aiming at a block takes this path instead of use(): sneaking with an item in hand
	 * makes vanilla skip the block and call useOn. Same helper, so both aims behave
	 * identically.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		return DisassembleHelper.splitInto(context, ModItems.KATANA, ModItems.WAKIZASHI);
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
