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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
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
 * range during the wind-up. The katana finisher works the same way now,
 * landing near the end of katana_slice.animation.json instead of
 * instantly on click.
 */
public class SwordComboHandler {

	// Matches attack_swing.animation.json's lunge-completion keyframe
	// (0.155s) - the point where the blade actually reaches full extension,
	// not the animation's full length (which now includes extra hold time
	// after the motion finishes) and not when you click.
	private static final int STAB_REACH_DELAY_TICKS = 3;
	private static final double STAB_REACH_DISTANCE = 4.0;

	// Matches katana_slice.animation.json's total length (0.432s = ~9 ticks)
	// - the finisher lands near the end of the slice, not the instant you
	// right-click.
	private static final int FINISHER_REACH_DELAY_TICKS = 9;
	private static final double FINISHER_REACH_DISTANCE = 4.0;

	private static final float STAB_DAMAGE = 1.0F;
	private static final float SOLO_FINISHER_DAMAGE_CAP = 20.0F;
	// The two real boss encounters (phases/mechanics, not just high HP) are
	// exempt from the combo finisher's instakill - everything else, Iron
	// Golem/Ravager tankiness included, still dies in one hit.
	private static final float BOSS_FINISHER_DAMAGE = 50.0F;
	private static final int STUN_DURATION_TICKS = 50;
	// Covers the whole stun window so the finisher stays usable for as long
	// as the target is actually stunned, not a shorter arbitrary cutoff.
	private static final int COMBO_WINDOW_TICKS = STUN_DURATION_TICKS;

	private static final Map<UUID, ComboState> activeCombos = new HashMap<>();
	private static final Map<UUID, PendingStab> pendingStabs = new HashMap<>();
	private static final Map<UUID, PendingFinisher> pendingFinishers = new HashMap<>();

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
		PendingFinisher busyFinisher = pendingFinishers.get(player.getUUID());
		if (busyFinisher != null && !busyFinisher.comboFinisher()) {
			// Katana is mid-slice on its own (no wakizashi stab paired with
			// it) - the wakizashi can't jump in and start a new stab until
			// that solo slice resolves.
			return InteractionResult.PASS;
		}

		pendingStabs.put(player.getUUID(),
				new PendingStab(target.getUUID(), serverPlayer.tickCount + STAB_REACH_DELAY_TICKS));

		return InteractionResult.FAIL;
	}

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		Iterator<Map.Entry<UUID, PendingStab>> stabIterator = pendingStabs.entrySet().iterator();
		while (stabIterator.hasNext()) {
			Map.Entry<UUID, PendingStab> entry = stabIterator.next();
			PendingStab pending = entry.getValue();

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.tickCount < pending.applyTick()) {
				if (player == null) {
					stabIterator.remove();
				}
				continue;
			}
			stabIterator.remove();

			ServerLevel level = player.level();
			if (!(level.getEntity(pending.targetId()) instanceof LivingEntity target) || !target.isAlive()) {
				continue;
			}
			if (player.distanceTo(target) > STAB_REACH_DISTANCE) {
				continue;
			}

			target.hurt(level.damageSources().playerAttack(player), STAB_DAMAGE);
			// Not a mob effect: a sword landing should not read as a thrown potion, and
			// Weakness only zeroed the damage while the mob kept swinging. StunAttachment
			// holds the target still and blocks its attacks outright, with no particles,
			// no HUD icon, and nothing milk can wash off. Same duration as before.
			StunAttachment.stun(target, STUN_DURATION_TICKS);
			playSweepEffect(level, target.getX(), target.getY(), target.getZ());

			activeCombos.put(player.getUUID(),
					new ComboState(target.getUUID(), player.tickCount + COMBO_WINDOW_TICKS));
		}

		Iterator<Map.Entry<UUID, PendingFinisher>> finisherIterator = pendingFinishers.entrySet().iterator();
		while (finisherIterator.hasNext()) {
			Map.Entry<UUID, PendingFinisher> entry = finisherIterator.next();
			PendingFinisher pending = entry.getValue();

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.tickCount < pending.applyTick()) {
				if (player == null) {
					finisherIterator.remove();
				}
				continue;
			}
			finisherIterator.remove();

			ServerLevel level = player.level();
			if (!(level.getEntity(pending.targetId()) instanceof LivingEntity target) || !target.isAlive()) {
				continue;
			}
			if (player.distanceTo(target) > FINISHER_REACH_DISTANCE) {
				continue;
			}

			if (pending.comboFinisher()) {
				StunAttachment.clear(target);
				// Paired with a landed wakizashi stab - instakill regardless
				// of remaining health/armor/resistance, except the Wither
				// and Ender Dragon, whose actual boss fights shouldn't be
				// skippable with one combo.
				boolean isRealBoss = target instanceof WitherBoss || target instanceof EnderDragon;
				target.hurt(level.damageSources().playerAttack(player),
						isRealBoss ? BOSS_FINISHER_DAMAGE : Float.MAX_VALUE);
			} else {
				// Katana on its own, no wakizashi stab in progress - same
				// slice, same animation, but not an instakill: half the
				// target's max health per hit, capped at 20 (an Enderman's
				// own half-health value, 40 max HP / 2) so it doesn't keep
				// scaling up forever against tankier mobs.
				target.hurt(level.damageSources().playerAttack(player),
						Math.min(target.getMaxHealth() * 0.5F, SOLO_FINISHER_DAMAGE_CAP));
			}
			playSweepEffect(level, target.getX(), target.getY(), target.getZ());
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
		if (player.getMainHandItem().getItem() != ModItems.KATANA) {
			return InteractionResult.PASS;
		}

		ComboState combo = activeCombos.get(player.getUUID());
		boolean comboFinisher = combo != null && player.tickCount <= combo.expireTick()
				&& combo.targetId().equals(target.getUUID());
		if (comboFinisher) {
			activeCombos.remove(player.getUUID());
		}

		if (player instanceof ServerPlayer serverPlayer) {
			pendingFinishers.put(player.getUUID(), new PendingFinisher(
					target.getUUID(), serverPlayer.tickCount + FINISHER_REACH_DELAY_TICKS, comboFinisher));
		}

		return InteractionResult.FAIL;
	}

	/**
	 * The paired stab is the Daisho Obi's reward, so it needs the swords actually
	 * drawn - not merely a katana and wakizashi happening to be in the two hands.
	 * Checking the held items alone let a player hand-assemble the loadout and use
	 * the combo without ever equipping an obi.
	 *
	 * The katana's solo slice is deliberately NOT gated this way: it is what makes
	 * a freshly forged sword feel like more than a retextured vanilla one, and
	 * having it fail would turn the payoff of the whole smithing chain into a
	 * suspected bug.
	 */
	private static boolean isWakizashiDrawn(Player player) {
		return player.hasAttached(DrawnSwordsAttachment.TYPE)
				&& player.getMainHandItem().getItem() == ModItems.KATANA
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

	private record PendingFinisher(UUID targetId, int applyTick, boolean comboFinisher) {
	}
}
