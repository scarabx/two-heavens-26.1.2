package net.scarabx.twoheavens.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

	// Vanilla has no blood particle. Block fragments rather than dust: dust motes
	// float and fade in place, while block particles have gravity, so they arc and
	// fall away. Behaving like a liquid does more for "this is blood" than any amount
	// of colour tuning. Nothing lands or accumulates - persistent splatter would be a
	// decal, not a particle.
	//
	// The source block is only a texture to tear fragments from - swap it freely.
	// Redstone block reads bright and slightly speckled; nether wart is darker and
	// matte, closer to blood.
	private static final BlockParticleOption BLOOD =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());

	// Moves are re-validated a few ticks after the click, by which time a mob's
	// knockback may have shoved the player out of range - the hit then vanishes with
	// no feedback at all. This margin keeps a swing that was fair when it was made.
	private static final double REACH_SLACK = 1.0;

	private static final float STAB_DAMAGE = 1.0F;
	// The wakizashi's no-obi cut. Deliberately shorter reach than the katana's
	// 4.0: the short blade has to be stepped in with, which is what turns "same
	// family, shorter weapon" from a look into something the player feels.
	private static final int CUT_REACH_DELAY_TICKS = 2;
	private static final double CUT_REACH_DISTANCE = 2.5;
	private static final float CUT_DAMAGE = 7.0F;
	private static final int CUT_SLOW_TICKS = 20;

	private static final float SOLO_FINISHER_DAMAGE_CAP = 20.0F;
	// Floor under the half-max-health scaling. Without it the percentage makes
	// EVERY target under 40 max HP die in exactly two hits - a cow takes 5 twice
	// just like a creeper takes 10 twice - so the blade feels identical against a
	// chicken and a zombie. Matches KatanaItem's own flat hit damage, so a slice
	// is never weaker than a plain katana blow.
	private static final float SOLO_FINISHER_DAMAGE_FLOOR = 12.0F;
	// The two real boss encounters (phases/mechanics, not just high HP) are
	// exempt from the combo finisher's instakill - everything else, Iron
	// Golem/Ravager tankiness included, still dies in one hit.
	private static final float BOSS_FINISHER_DAMAGE = 50.0F;
	private static final int STUN_DURATION_TICKS = 50;

	/**
	 * How long a mob shrugs off further stuns after one ends. PER MOB, not per player,
	 * so a second attacker can still be stunned while the first is recovering - a crowd
	 * stays handleable.
	 *
	 * Without this, stun was worthless BECAUSE it was unlimited: attacks have no rate
	 * limit, so you re-stunned before the previous stun did anything, and the finisher
	 * stopped being a payoff for setting something up and became your normal attack
	 * with extra steps. Rationing the window is what gives it weight back.
	 *
	 * Deliberately NOT a cooldown on attacking. The blades apply damage manually and
	 * carry no Tool component precisely so hits never scale with swing timing, and that
	 * fast loose feel is what makes the combat survivable. Slowing the sword to fix the
	 * stun would trade the good half for the broken one.
	 */
	private static final int STUN_IMMUNITY_TICKS = 60;
	// Covers the whole stun window so the finisher stays usable for as long
	// as the target is actually stunned, not a shorter arbitrary cutoff.
	private static final int COMBO_WINDOW_TICKS = STUN_DURATION_TICKS;

	private static final Map<UUID, ComboState> activeCombos = new HashMap<>();

	/**
	 * Per-mob: the tick each target can be stunned again, measured against the MOB's own
	 * tickCount - two players have unrelated counters, so a shared per-mob map cannot be
	 * keyed off whoever happened to land the hit.
	 *
	 * Swept periodically rather than on mob death: nothing tells us when an arbitrary
	 * entity is removed, and a stale entry is only a few bytes until it is cleared.
	 */
	private static final Map<UUID, Integer> stunReadyAt = new HashMap<>();

	/** Server tick each entry was written, so the map can be aged out without an entity scan. */
	private static final Map<UUID, Integer> stunWrittenAt = new HashMap<>();
	private static final Map<UUID, PendingStab> pendingStabs = new HashMap<>();
	private static final Map<UUID, PendingFinisher> pendingFinishers = new HashMap<>();
	private static final Map<UUID, PendingCut> pendingCuts = new HashMap<>();

	public static void register() {
		AttackEntityCallback.EVENT.register(SwordComboHandler::onAttackEntity);
		PayloadTypeRegistry.serverboundPlay().register(MovePayload.TYPE, MovePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MovePayload.TYPE, (payload, context) -> {
			ServerPlayer mover = context.player();
			context.server().execute(() -> onMove(mover, payload.move(), payload.targetId()));
		});
		ServerTickEvents.END_SERVER_TICK.register(SwordComboHandler::onServerTick);
	}

	private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand,
			Entity entity, EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(entity instanceof LivingEntity target)) {
			return InteractionResult.PASS;
		}
		// Undrawn, the katana is a right-click weapon only - left-click does
		// nothing at all rather than falling through to a vanilla hit. This runs
		// on BOTH sides deliberately: Fabric fires this callback client-side too,
		// where a non-PASS result cancels MultiPlayerGameMode#attack before it
		// swings the arm or sends the attack packet. Blocking server-side alone
		// stopped the damage but left the click looking like it connected.
		if (!isWakizashiDrawn(player) && player.getMainHandItem().getItem() == ModItems.KATANA) {
			return InteractionResult.FAIL;
		}
		// Server-only from here. The wakizashi's branch in particular must NOT run
		// client-side: a non-PASS result there cancels the swing, and the swing is
		// what AttackSwingController watches to play the cut animation.
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!isWakizashiDrawn(player)) {
			// The undrawn wakizashi's cut does NOT arrive here: its left-click is
			// intercepted client-side before vanilla runs the attack, so this callback
			// never fires for it. Every move comes in over MovePayload instead.
			return InteractionResult.PASS;
		}
		// Cancel vanilla's attack and nothing more - the stab itself is scheduled from
		// MovePayload, so that a target the crosshair missed can still be acquired.
		return InteractionResult.FAIL;
	}

	/**
	 * Every move arrives here, scheduled the same way: sweep immediately, resolve a
	 * target, re-validate, then apply a few ticks later when the blade arrives.
	 *
	 * Scheduling lives here rather than in the Fabric callbacks because those only
	 * fire when vanilla already found an entity under the crosshair. A mob below the
	 * aim point - a chicken at your feet, a spider you are standing over - was never
	 * acquired at all, so the move did nothing rather than missing. The callbacks
	 * still run, but only to cancel vanilla's own attack.
	 */
	private static void onMove(ServerPlayer player, int move, int targetId) {
		if (!isMoveAllowed(player, move)) {
			return;
		}
		// A click aimed at a block is not a swing at the world. No sweep, no sound, no
		// move - the client suppresses the animation to match.
		//
		// The check has to be HERE. The moves are triggered off the key press by
		// AttackSwingController and never go through vanilla's attack path, so
		// AttackBlockCallback and the swing mixin never see them - an earlier attempt to
		// guard it there did nothing at all.
		//
		// Air still sweeps, deliberately: the moves are usable for their own sake, and
		// only a block suppresses them.
		if (aimingAtBlock(player, FINISHER_REACH_DISTANCE)) {
			return;
		}

		// Before any target resolution: the move was made, so it sounds and looks like
		// one even if it hits nothing.
		playSweepAt(player);

		double reach = switch (move) {
			case MovePayload.STAB -> STAB_REACH_DISTANCE;
			case MovePayload.CUT -> CUT_REACH_DISTANCE;
			default -> FINISHER_REACH_DISTANCE;
		};
		// The tutorial follows the INPUT, not the outcome - above the target check on
		// purpose. These moves swing at air perfectly well; only the stun needs a mob,
		// and the stun is the one part of this that is mob-specific. Requiring something
		// in range would mean the tutorial refused to acknowledge a move the player just
		// watched themselves perform, which is the worst thing a tutorial can say.
		//
		// Every step is like this: no gating anywhere, no grading of the outcome. It also
		// means the stun cooldown can never stall the sequence, and it works identically
		// whether the player is practising in an empty field or mid-firefight.
		switch (move) {
			case MovePayload.STAB -> CombatTutorialAttachment.advance(player,
					CombatTutorialAttachment.STUN, CombatTutorialAttachment.FINISH);
			case MovePayload.CUT -> { }
			default -> CombatTutorialAttachment.advance(player,
					CombatTutorialAttachment.FINISH, CombatTutorialAttachment.DONE);
		}

		LivingEntity target = resolveTarget(player, targetId, reach);
		if (target == null) {
			return;
		}

		UUID id = player.getUUID();
		switch (move) {
			case MovePayload.STAB -> {
				PendingFinisher busy = pendingFinishers.get(id);
				if (busy != null && !busy.comboFinisher()) {
					// Katana is mid-slice on its own - the wakizashi can't jump in and
					// start a new stab until that solo slice resolves.
					return;
				}
				pendingStabs.put(id, new PendingStab(target.getUUID(),
						player.tickCount + STAB_REACH_DELAY_TICKS));
			}
			case MovePayload.CUT -> pendingCuts.put(id, new PendingCut(target.getUUID(),
					player.tickCount + CUT_REACH_DELAY_TICKS));
			default -> {
				ComboState combo = activeCombos.get(id);
				boolean comboFinisher = combo != null && player.tickCount <= combo.expireTick()
						&& combo.targetId().equals(target.getUUID());
				if (comboFinisher) {
					activeCombos.remove(id);
				}
				pendingFinishers.put(id, new PendingFinisher(target.getUUID(),
						player.tickCount + FINISHER_REACH_DELAY_TICKS, comboFinisher));
			}
		}
	}

	/**
	 * True when the crosshair is on a block that ANSWERS A CLICK - approximated by having
	 * a block entity, since Minecraft has no interactable query. Matches the client check
	 * in AttackSwingController: the two must agree, or the animation plays with no sweep
	 * behind it, or the reverse.
	 *
	 * An entity in front of the block wins: standing a mob against a chest must not make
	 * it unattackable.
	 */
	private static boolean aimingAtBlock(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end,
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK
				|| !InteractableBlocks.answersClick(player.level(), hit.getBlockPos())) {
			return false;
		}
		double blockDistance = hit.getLocation().distanceTo(eye);
		for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(reach), e -> e != player && e.isAlive())) {
			if (nearby.position().distanceTo(eye) < blockDistance
					&& player.getLookAngle().dot(nearby.position().subtract(eye).normalize()) > 0.9) {
				return false;
			}
		}
		return true;
	}

	private static boolean isMoveAllowed(ServerPlayer player, int move) {
		return switch (move) {
			case MovePayload.STAB -> isWakizashiDrawn(player);
			// Undrawn with the wakizashi in the mainhand (left-click), or drawn with
			// it in the offhand (middle-click).
			case MovePayload.CUT -> isWakizashiDrawn(player)
					|| player.getMainHandItem().getItem() == ModItems.WAKIZASHI;
			default -> player.getMainHandItem().getItem() == ModItems.KATANA;
		};
	}

	private static LivingEntity resolveTarget(ServerPlayer player, int targetId, double reach) {
		LivingEntity target = targetId >= 0
				&& player.level().getEntity(targetId) instanceof LivingEntity aimed ? aimed : null;
		if (target == null) {
			target = acquireTarget(player, reach);
		}
		if (target == null || target == player || !target.isAlive()) {
			return null;
		}
		return target;
	}

	/** How often the stun-immunity map is swept for entries no live mob can still use. */
	private static final int STUN_SWEEP_INTERVAL_TICKS = 600;

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		// Keyed by mob UUID, so entries outlive the mobs that made them. Swept by AGE
		// rather than by checking which mobs are still alive: liveness would mean walking
		// every entity in every level, which is a real cost on a busy world to reclaim a
		// few bytes. An entry is useless once its immunity could not still be running, so
		// age alone is enough - and a mob whose entry was dropped early is simply
		// stunnable again, which is the correct outcome anyway.
		if (server.getTickCount() % STUN_SWEEP_INTERVAL_TICKS == 0) {
			stunReadyAt.values().removeIf(deadline -> deadline <= 0);
			int cutoff = STUN_DURATION_TICKS + STUN_IMMUNITY_TICKS + STUN_SWEEP_INTERVAL_TICKS;
			stunWrittenAt.entrySet().removeIf(entry -> {
				if (server.getTickCount() - entry.getValue() < cutoff) {
					return false;
				}
				stunReadyAt.remove(entry.getKey());
				return true;
			});
		}

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
			if (outOfReach(player, target, STAB_REACH_DISTANCE)) {
				continue;
			}

			// Non-stackable: a mob already stunned, or still shrugging one off, takes the
			// hit and its damage but cannot be locked down by chaining stabs.
			boolean stunnable = !StunAttachment.isStunned(target)
					&& target.tickCount >= stunReadyAt.getOrDefault(target.getUUID(), 0);

			target.hurt(level.damageSources().playerAttack(player), STAB_DAMAGE);
			// Not a mob effect: a sword landing should not read as a thrown potion, and
			// Weakness only zeroed the damage while the mob kept swinging. StunAttachment
			// holds the target still and blocks its attacks outright, with no particles,
			// no HUD icon, and nothing milk can wash off. Same duration as before.
			if (stunnable) {
				StunAttachment.stun(target, STUN_DURATION_TICKS);
				stunReadyAt.put(target.getUUID(),
						target.tickCount + STUN_DURATION_TICKS + STUN_IMMUNITY_TICKS);
				stunWrittenAt.put(target.getUUID(), server.getTickCount());
				activeCombos.put(player.getUUID(),
						new ComboState(target.getUUID(), player.tickCount + COMBO_WINDOW_TICKS));
			}
			playSweepEffect(level, target.getX(), target.getY(), target.getZ());
			playBloodEffect(level, player, target);

		}

		Iterator<Map.Entry<UUID, PendingCut>> cutIterator = pendingCuts.entrySet().iterator();
		while (cutIterator.hasNext()) {
			Map.Entry<UUID, PendingCut> entry = cutIterator.next();
			PendingCut pending = entry.getValue();

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.tickCount < pending.applyTick()) {
				if (player == null) {
					cutIterator.remove();
				}
				continue;
			}
			cutIterator.remove();

			ServerLevel level = player.level();
			if (!(level.getEntity(pending.targetId()) instanceof LivingEntity target) || !target.isAlive()) {
				continue;
			}
			if (outOfReach(player, target, CUT_REACH_DISTANCE)) {
				continue;
			}

			target.hurt(level.damageSources().playerAttack(player), CUT_DAMAGE);
			StunAttachment.slow(target, CUT_SLOW_TICKS);
			playSweepEffect(level, target.getX(), target.getY(), target.getZ());
			playBloodEffect(level, player, target);
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
			if (outOfReach(player, target, FINISHER_REACH_DISTANCE)) {
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
				// scaling up forever against tankier mobs, and floored at 12
				// so weak mobs die in one hit instead of the percentage making
				// everything under 40 max HP a uniform two-hit kill.
				float scaled = Math.min(target.getMaxHealth() * 0.5F, SOLO_FINISHER_DAMAGE_CAP);
				target.hurt(level.damageSources().playerAttack(player),
						Math.max(scaled, SOLO_FINISHER_DAMAGE_FLOOR));
			}
			playSweepEffect(level, target.getX(), target.getY(), target.getZ());
			playBloodEffect(level, player, target);
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

		// Cancel vanilla's interaction and nothing more - scheduled from MovePayload.
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

	/**
	 * Sweep for a move that hit nothing.
	 *
	 * Spawned a short way along the player's look vector at chest height rather than
	 * at the player's position - their position is their feet, where the particle is
	 * hidden inside the model and reads as no particle at all.
	 *
	 * Deliberately NOT vanilla's placement from Player#attack (one block out along
	 * the yaw at mid-body height, count 0, direction as velocity). That was tried and
	 * looked worse here: this version reads better against these animations, and
	 * matches how the on-hit sweep is spawned so a miss and a hit look alike.
	 */
	private static void playSweepAt(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		playSweepEffect(player.level(),
				player.getX() + look.x * 1.5,
				player.getY() + player.getEyeHeight() * 0.75 + look.y * 1.5,
				player.getZ() + look.z * 1.5);
	}

	/**
	 * Blood at the point the blade meets the mob: on the target's near side, facing
	 * the attacker, at roughly chest height rather than at its feet-anchored position.
	 *
	 * Spawned with a spread and an outward drift so it reads as a spray off the cut
	 * rather than a static cloud - and since these have gravity, they arc away as they
	 * fade rather than hanging where they were struck.
	 */
	/**
	 * Reach measured generously: the move connects if EITHER the old feet-to-feet
	 * distance OR the eye-to-bounding-box distance is within range.
	 *
	 * Entity#position is the point between the feet, and distanceTo compares those
	 * points while ignoring the bounding box - so a spider 1.4 blocks wide could be
	 * touching the player with its centre outside reach. Measuring eye-to-box fixes
	 * that, but measuring from the eyes adds the player's own height against short
	 * mobs at their feet, which made low targets harder to land in practice.
	 *
	 * Taking whichever is smaller keeps the box benefit for wide and tall mobs
	 * without ever being stricter than the original check for low ones. Reach is a
	 * feel value, not a simulation - erring generous is correct here.
	 *
	 * REACH_SLACK is added on top, because this is re-checked when the move LANDS, a
	 * few ticks after the click: without it a mob's knockback shoves the player out of
	 * range mid-swing and the hit vanishes with no feedback at all. A target that
	 * walks away is still missed - the margin only covers the player being moved.
	 */
	/**
	 * Finds a target when the crosshair has none.
	 *
	 * Every move needs an entity to act on, and the crosshair only reports what the
	 * player is looking AT - so a chicken or a spider below the aim point is never
	 * acquired, and the move does nothing at all rather than missing. A blade swung
	 * down through something standing at your feet should connect.
	 *
	 * Picks the closest living entity whose box is within reach and in front of the
	 * player (dot product against the look vector), so this only ever helps with
	 * targets the player was plausibly swinging at - never something behind them.
	 */
	private static LivingEntity acquireTarget(ServerPlayer player, double reach) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(reach + REACH_SLACK))) {
			if (candidate == player || !candidate.isAlive() || outOfReach(player, candidate, reach)) {
				continue;
			}
			Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(eye);
			if (toCandidate.dot(look) <= 0.0) {
				continue;
			}
			double distance = toCandidate.lengthSqr();
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	private static boolean outOfReach(ServerPlayer player, LivingEntity target, double reach) {
		double allowed = reach + REACH_SLACK;
		if (player.distanceTo(target) <= allowed) {
			return false;
		}

		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		double dx = Math.max(box.minX - eye.x, Math.max(0.0, eye.x - box.maxX));
		double dy = Math.max(box.minY - eye.y, Math.max(0.0, eye.y - box.maxY));
		double dz = Math.max(box.minZ - eye.z, Math.max(0.0, eye.z - box.maxZ));
		return dx * dx + dy * dy + dz * dz > allowed * allowed;
	}

	private static void playBloodEffect(ServerLevel level, ServerPlayer attacker, LivingEntity target) {
		Vec3 toAttacker = attacker.position().subtract(target.position());
		double length = toAttacker.horizontalDistance();
		Vec3 facing = length < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : toAttacker.scale(1.0 / length);
		double reach = target.getBbWidth() * 0.5;

		level.sendParticles(BLOOD,
				target.getX() + facing.x * reach,
				target.getY() + target.getBbHeight() * 0.6,
				target.getZ() + facing.z * reach,
				34, 0.22, 0.28, 0.22, 0.13);
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

	private record PendingCut(UUID targetId, int applyTick) {
	}
}
