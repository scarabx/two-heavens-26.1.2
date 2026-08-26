package net.scarabx.twoheavens.block.entity;

import net.scarabx.twoheavens.block.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.custom.TataraFurnaceFiredBlock;

public class TataraFurnaceFiredBlockEntity extends BlockEntity {

	// 8 steps of 150 ticks (7.5s) each = 1200 ticks (60s) total, same rhythm
	// as curing. First 4 steps (0-30s) are passive-only, bellows locked out.
	// Last 4 steps (30-60s) each need a recent-enough bellows use to climb -
	// miss one and that step falls back one level instead, floored at the
	// passive cap (level 4). Heat only ever moves in these whole-step jumps,
	// never a smooth per-tick trickle.
	// 20 seconds of passive heat, then 15 with the bellows. The two halves are set
	// separately rather than split from a total, because they are not worth the same:
	// the passive phase is waiting and the bellows phase is play, so trimming came off
	// the first. Long enough that the heat visibly rises on its own before you help it.
	private static final int PASSIVE_PHASE_TICKS = 400;
	private static final int BELLOWS_PHASE_TICKS = 300;
	public static final int SMELT_DURATION_TICKS = PASSIVE_PHASE_TICKS + BELLOWS_PHASE_TICKS;
	/** Four passive heat steps, one per 5 seconds, taking it to the halfway cap. */
	private static final int STEP_TICKS = PASSIVE_PHASE_TICKS / 4;
	/** Who hears the "tend the furnace" call - anyone plausibly still working it. */
	private static final int TEND_ALERT_RANGE = 24;
	private static final float STARTING_HEAT = 0.0F;
	private static final float MAX_HEAT = 100.0F;
	private static final float PASSIVE_CAP = MAX_HEAT / 2.0F;
	private static final float STEP_HEAT = MAX_HEAT / 8.0F;

	// A bellows use only counts toward the step it's used within if it
	// happened within this long of that step completing - i.e. bellow at
	// least once every 5 seconds, same cadence regardless of the 7.5s step
	// length.
	// Purely an anti-spam debounce on the sound/durability cost, not part of
	// the actual heat mechanic - mashing the button shouldn't waste durability
	// or blast the gust sound every tick.
	private static final int BELLOWS_CLICK_DEBOUNCE_TICKS = 10; // 0.5s

	private int smeltTicks = 0;
	private float heat = 0.0F;
	// The visual layer only ever climbs, even if heat itself falls back from
	// neglect - the satetsu physically settling lower doesn't "regrow" just
	// because it cooled off a bit, only redness/heat would realistically
	// fade, and this block has no separate color property to fade
	// independently of layer height (unlike the unfired furnace's
	// BURN_STAGE/COLOR_STAGE split).
	private int peakStage = 0;
	private long lastBellowsGameTime = Long.MIN_VALUE / 2;
	private boolean reachedCap = false;

	public TataraFurnaceFiredBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TATARA_FURNACE_FIRED, pos, state);
	}

	public void startSmelting() {
		this.smeltTicks = 0;
		this.heat = STARTING_HEAT;
		this.peakStage = 0;
		this.reachedCap = false;
		this.lastBellowsGameTime = Long.MIN_VALUE / 2;
		this.setChanged();
	}

	public enum BellowsResult {
		TOO_EARLY,
		ON_COOLDOWN,
		ACKNOWLEDGED
	}

	/**
	 * Each accepted pump raises the heat one step, and heat never falls again.
	 *
	 * It used to work the other way: heat rose only if a pump had landed within 5
	 * seconds of an invisible step boundary, and fell back a step otherwise. That made
	 * a single missed beat cost double - the step lost plus the step to re-earn - and
	 * gave no way to see why, since both the window and the boundary were invisible.
	 * A count you can state ("four pumps") is a rule a player can learn; a cadence
	 * measured against something they cannot see is not.
	 */
	public BellowsResult onBellowsUsed() {
		if (this.smeltTicks < PASSIVE_PHASE_TICKS) {
			return BellowsResult.TOO_EARLY;
		}

		long ticksSinceLastClick = this.level == null ? Long.MAX_VALUE : this.level.getGameTime() - this.lastBellowsGameTime;
		if (ticksSinceLastClick < BELLOWS_CLICK_DEBOUNCE_TICKS) {
			return BellowsResult.ON_COOLDOWN;
		}

		if (this.level != null) {
			this.lastBellowsGameTime = this.level.getGameTime();
			this.heat = Math.min(MAX_HEAT, this.heat + STEP_HEAT);
			setChanged();
		}
		this.setChanged();
		return BellowsResult.ACKNOWLEDGED;
	}

	public void serverTick(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(TataraFurnaceFiredBlock.LIT) || state.getValue(TataraFurnaceFiredBlock.KERA_FORMED)) {
			return;
		}

		this.smeltTicks++;

		// The passive half just ended - from here neglect costs heat, so tell anyone
		// nearby to pick up the bellows. Nothing on the block itself signals this.
		if (this.smeltTicks == PASSIVE_PHASE_TICKS && level instanceof ServerLevel serverLevel) {
			Component tend = Component.translatable("message.twoheavens.tend_furnace")
					.withStyle(ChatFormatting.GOLD);
			for (ServerPlayer player : serverLevel.getPlayers(p -> p.blockPosition().closerThan(pos, TEND_ALERT_RANGE))) {
				player.sendSystemMessage(tend);
			}
		}

		if (this.smeltTicks % STEP_TICKS == 0 && this.smeltTicks <= PASSIVE_PHASE_TICKS) {
			// Passive half: heat climbs on its own to the halfway cap. Past that it only
			// moves when the player pumps, and it never falls - missing a moment costs
			// nothing but the time you were already spending.
			this.heat = Math.min(PASSIVE_CAP, this.heat + STEP_HEAT);
		}

		int currentStage = Math.max(0, Math.min(8, Math.round(this.heat / STEP_HEAT)));
		this.peakStage = Math.max(this.peakStage, currentStage);
		if (state.getValue(TataraFurnaceFiredBlock.SMELT_STAGE) != this.peakStage
				|| state.getValue(TataraFurnaceFiredBlock.REDNESS_STAGE) != currentStage) {
			level.setBlock(pos, state.setValue(TataraFurnaceFiredBlock.SMELT_STAGE, this.peakStage)
					.setValue(TataraFurnaceFiredBlock.REDNESS_STAGE, currentStage), 3);
		}

		// Once it first reaches the passive cap (30s / halfway), smoke shuts
		// off for good, even if heat later dips back down from a missed step.
		if (this.heat >= PASSIVE_CAP) {
			this.reachedCap = true;
		}

		if (level instanceof ServerLevel serverLevel) {
			RandomSource random = level.getRandom();

			if (!this.reachedCap) {
				// Smoke steps up with each reddening stage - hotter and redder means more violent smoke.
				int smokeChanceOutOf100 = Math.min(95, 30 + currentStage * 20);
				int smokeCount = 2 + currentStage;
				if (random.nextInt(100) < smokeChanceOutOf100) {
					serverLevel.sendParticles(ParticleTypes.SMOKE,
							pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
							smokeCount, 0.2, 0.1, 0.2, 0.01);
				}
			}

			// Quiet, intermittent crackle for the whole Kera-making process
			// (both the passive first half and the bellows-driven second
			// half) - kept low/infrequent so it never masks the louder
			// whoosh (BLAZE_SHOOT) or bellows gust (ENDER_DRAGON_FLAP).
			if (random.nextInt(40) == 0) {
				level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
						0.5F, 0.8F + random.nextFloat() * 0.4F);
			}
		}

		// Timer alone isn't enough to finish - heat has to have actually been
		// driven all the way up to MAX_HEAT via the second-half bellows work.
		if (this.smeltTicks >= SMELT_DURATION_TICKS && this.heat >= MAX_HEAT) {
			level.setBlock(pos, state.setValue(TataraFurnaceFiredBlock.KERA_FORMED, true)
					.setValue(TataraFurnaceFiredBlock.SMELT_STAGE, 8)
					.setValue(TataraFurnaceFiredBlock.REDNESS_STAGE, 8), 3);
			level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.6F);
		}
	}

	@Override
	protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(ModDataComponents.FURNACE_PROGRESS,
				new ModDataComponents.FurnaceProgress(this.smeltTicks, this.heat));
	}

	@Override
	protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		ModDataComponents.FurnaceProgress progress = getter.get(ModDataComponents.FURNACE_PROGRESS);
		if (progress != null) {
			this.smeltTicks = progress.ticks();
			this.heat = progress.heat();
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("SmeltTicks", this.smeltTicks);
		output.putFloat("Heat", this.heat);
		output.putInt("PeakStage", this.peakStage);
		output.putLong("LastBellows", this.lastBellowsGameTime);
		output.putBoolean("ReachedCap", this.reachedCap);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.smeltTicks = input.getIntOr("SmeltTicks", 0);
		this.heat = input.getFloatOr("Heat", 0.0F);
		this.peakStage = input.getIntOr("PeakStage", 0);
		this.lastBellowsGameTime = input.getLongOr("LastBellows", Long.MIN_VALUE / 2);
		this.reachedCap = input.getBooleanOr("ReachedCap", false);
	}
}
