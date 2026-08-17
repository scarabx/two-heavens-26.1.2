package net.scarabx.twoheavens.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.scarabx.twoheavens.item.ModItems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative wakizashi-stab / katana-finisher combo. Left-clicking
 * an entity while the wakizashi is drawn (real offhand item, checked
 * server-side - not trusting client animation state) schedules the stab
 * damage/stun for when the attack_swing animation actually reaches full
 * extension (STAB_REACH_DELAY_TICKS later), re-checking range at that
 * moment - a click doesn't guarantee a hit if the target moves out of
 * range during the wind-up. The katana finisher stays instant (its own
 * animation has no comparable wind-up).
 */
public class SwordComboHandler {

	// Matches attack_swing.animation.json's total length (1.4s) - the point
	// where the blade actually reaches full extension, not when you click.
	private static final int STAB_REACH_DELAY_TICKS = 28;
	private static final double STAB_REACH_DISTANCE = 4.0;

	private static final float STAB_DAMAGE = 1.0F;
	private static final int STUN_DURATION_TICKS = 100;
	// Covers the whole stun window so the finisher stays usable for as long
	// as the target is actually stunned, not a shorter arbitrary cutoff.
	private static final int COMBO_WINDOW_TICKS = STUN_DURATION_TICKS;

	private static final Map<UUID, ComboState> activeCombos = new HashMap<>();
	private static final Map<UUID, PendingStab> pendingStabs = new HashMap<>();

	public static void register() {
		AttackEntityCallback.EVENT.register(SwordComboHandler::onAttackEntity);
		UseEntityCallback.EVENT.register(SwordComboHandler::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(SwordComboHandler::onServerTick);
	}

	private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand,
			Entity entity, EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!(entity instanceof LivingEntity target) || !isWakizashiDrawn(player)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		pendingStabs.put(player.getUUID(),
				new PendingStab(target.getUUID(), serverPlayer.tickCount + STAB_REACH_DELAY_TICKS));

		return InteractionResult.FAIL;
	}

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		Iterator<Map.Entry<UUID, PendingStab>> iterator = pendingStabs.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PendingStab> entry = iterator.next();
			PendingStab pending = entry.getValue();

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.tickCount < pending.applyTick()) {
				if (player == null) {
					iterator.remove();
				}
				continue;
			}
			iterator.remove();

			ServerLevel level = player.level();
			if (!(level.getEntity(pending.targetId()) instanceof LivingEntity target) || !target.isAlive()) {
				continue;
			}
			if (player.distanceTo(target) > STAB_REACH_DISTANCE) {
				continue;
			}

			target.hurt(level.damageSources().playerAttack(player), STAB_DAMAGE);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_DURATION_TICKS, 9));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_DURATION_TICKS, 9));
			playSweepEffect(level, target.getX(), target.getY(), target.getZ());

			activeCombos.put(player.getUUID(),
					new ComboState(target.getUUID(), player.tickCount + COMBO_WINDOW_TICKS));
		}
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
			Entity entity, EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!(entity instanceof LivingEntity target)) {
			return InteractionResult.PASS;
		}

		ComboState combo = activeCombos.get(player.getUUID());
		if (combo == null || player.tickCount > combo.expireTick() || !combo.targetId().equals(target.getUUID())) {
			return InteractionResult.PASS;
		}
		if (!(player.getMainHandItem().getItem() == ModItems.KATANA)) {
			return InteractionResult.PASS;
		}

		activeCombos.remove(player.getUUID());
		target.removeEffect(MobEffects.SLOWNESS);
		target.removeEffect(MobEffects.WEAKNESS);
		// Instakill - the finisher always ends the target regardless of
		// remaining health/armor/resistance.
		target.hurt(level.damageSources().playerAttack(player), Float.MAX_VALUE);
		playSweepEffect(level, target.getX(), target.getY(), target.getZ());

		return InteractionResult.FAIL;
	}

	private static boolean isWakizashiDrawn(Player player) {
		return player.getMainHandItem().getItem() == ModItems.KATANA
				&& player.getOffhandItem().getItem() == ModItems.WAKIZASHI;
	}

	private static void playSweepEffect(Level level, double x, double y, double z) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	private record ComboState(UUID targetId, int expireTick) {
	}

	private record PendingStab(UUID targetId, int applyTick) {
	}
}
