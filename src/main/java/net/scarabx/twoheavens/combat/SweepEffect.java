package net.scarabx.twoheavens.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The katana/wakizashi sweep sound + SWEEP_ATTACK particle, shared between
 * the on-hit path (KatanaItem/WakizashiItem#hurtEnemy) and the on-swing path
 * (LivingEntitySwingMixin, for swings that don't land a hit at all). Tracks
 * the last server tick a hit-triggered sweep played per player so the swing
 * mixin - which fires for every swing, hit or miss - doesn't also play a
 * second overlapping sweep on the same tick a hit already produced one.
 */
public final class SweepEffect {

	private static final Map<UUID, Integer> lastHitTick = new HashMap<>();

	private SweepEffect() {
	}

	public static void playFromHit(LivingEntity attacker) {
		play(attacker);
		if (attacker instanceof ServerPlayer serverPlayer) {
			lastHitTick.put(serverPlayer.getUUID(), serverPlayer.tickCount);
		}
	}

	public static void playFromSwing(ServerPlayer player) {
		Integer hitTick = lastHitTick.get(player.getUUID());
		if (hitTick != null && hitTick == player.tickCount) {
			return;
		}
		play(player);
	}

	private static void play(LivingEntity entity) {
		Level level = entity.level();

		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.0F);

		if (level instanceof ServerLevel serverLevel) {
			Vec3 lookVec = entity.getViewVector(1.0F);
			Vec3 pos = entity.position().add(0, entity.getBbHeight() * 0.7, 0);
			double xOffset = lookVec.x * 1.5;
			double zOffset = lookVec.z * 1.5;
			serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
					pos.x + xOffset, pos.y, pos.z + zOffset,
					1, 0, 0, 0, 0);
		}
	}
}
