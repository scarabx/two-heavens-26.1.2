package net.scarabx.twoheavens.item.custom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class WakizashiItem extends Item {

	public WakizashiItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		Level level = attacker.level();

		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.0F);

		if (level instanceof ServerLevel serverLevel) {
			Vec3 lookVec = attacker.getViewVector(1.0F);
			Vec3 pos = attacker.position().add(0, attacker.getBbHeight() * 0.7, 0);
			double xOffset = lookVec.x * 1.5;
			double zOffset = lookVec.z * 1.5;
			serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
					pos.x + xOffset, pos.y, pos.z + zOffset,
					1, 0, 0, 0, 0);
		}

		if (attacker instanceof Player player) {
			player.getCooldowns().addCooldown(stack, 10);
		}
	}
}
