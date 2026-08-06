package net.scarabx.twoheavens.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.scarabx.twoheavens.block.entity.TataraFurnaceBlockEntity;
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

		boolean used = false;
		if (level.getBlockEntity(pos) instanceof TataraFurnaceBlockEntity unfired) {
			unfired.onBellowsUsed();
			used = true;
		} else if (level.getBlockEntity(pos) instanceof TataraFurnaceFiredBlockEntity fired) {
			fired.onBellowsUsed();
			used = true;
		}

		if (!used) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide()) {
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
		}

		return InteractionResult.SUCCESS;
	}
}
