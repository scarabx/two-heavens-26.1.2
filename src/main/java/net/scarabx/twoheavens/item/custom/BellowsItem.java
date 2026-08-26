package net.scarabx.twoheavens.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.item.ItemRecipeTooltip;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.scarabx.twoheavens.block.entity.TataraFurnaceFiredBlockEntity;

public class BellowsItem extends Item {

	public BellowsItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();

		if (!(level.getBlockEntity(pos) instanceof TataraFurnaceFiredBlockEntity fired)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		TataraFurnaceFiredBlockEntity.BellowsResult result = fired.onBellowsUsed();
		if (result != TataraFurnaceFiredBlockEntity.BellowsResult.ACKNOWLEDGED) {
			// Too early (still in the passive-only first half) or mashed
			// again within the anti-spam debounce - no durability spent, no
			// sound, so spamming the button doesn't waste the bellows or
			// blast the gust sound every tick.
			return InteractionResult.SUCCESS;
		}

		level.playSound(null, pos, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.BLOCKS, 0.6F, 1.6F);
		ItemStack stack = context.getItemInHand();
		if (player != null) {
			stack.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
		}

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.GUST,
					pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
					6, 0.3, 0.2, 0.3, 0.05);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		consumer.accept(Component.translatable("tooltip.twoheavens.bellows_use"));
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}
}
