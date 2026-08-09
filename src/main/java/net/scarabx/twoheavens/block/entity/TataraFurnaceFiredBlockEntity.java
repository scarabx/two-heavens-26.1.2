package net.scarabx.twoheavens.block.entity;

import net.minecraft.core.BlockPos;
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

	public static final int SMELT_DURATION_TICKS = 1200; // 1 minute
	private static final int BELLOWS_BOOST_WINDOW = 100; // 5s - bellows must be reused about this often to keep climbing
	private static final float STARTING_HEAT = 50.0F;
	private static final float PASSIVE_CAP = 70.0F; // fire alone gets you here over roughly the first 30 seconds, no bellows needed
	private static final float PASSIVE_HEAT_PER_TICK = (PASSIVE_CAP - STARTING_HEAT) / 600.0F; // ~30 seconds to reach the cap
	private static final float BOOSTED_HEAT_PER_TICK = 0.1F; // bellows-driven climb above the cap, toward MAX_HEAT
	private static final float FALLBACK_PER_TICK = 0.1F; // above the cap, sinks back toward it (never below) if not bellowed
	private static final float MAX_HEAT = 100.0F;

	private int smeltTicks = 0;
	private float heat = 0.0F;
	private long lastBellowsGameTime = Long.MIN_VALUE / 2;

	public TataraFurnaceFiredBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TATARA_FURNACE_FIRED, pos, state);
	}

	public void startSmelting() {
		this.smeltTicks = 0;
		this.heat = STARTING_HEAT;
		if (this.level != null) {
			this.lastBellowsGameTime = this.level.getGameTime();
		}
		this.setChanged();
	}

	public void onBellowsUsed() {
		if (this.level != null) {
			this.lastBellowsGameTime = this.level.getGameTime();
		}
		this.setChanged();
	}

	public void serverTick(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(TataraFurnaceFiredBlock.LIT) || state.getValue(TataraFurnaceFiredBlock.KERA_FORMED)) {
			return;
		}

		long ticksSinceBellows = level.getGameTime() - this.lastBellowsGameTime;
		if (ticksSinceBellows < BELLOWS_BOOST_WINDOW) {
			// Actively bellowed recently - can climb past the passive cap, up to MAX_HEAT.
			this.heat = Math.min(MAX_HEAT, this.heat + BOOSTED_HEAT_PER_TICK);
		} else if (this.heat > PASSIVE_CAP) {
			// Not bellowed enough - settles back toward the passive cap, never below it.
			this.heat = Math.max(PASSIVE_CAP, this.heat - FALLBACK_PER_TICK);
		} else if (this.heat < PASSIVE_CAP) {
			// The fire alone keeps burning up toward the passive cap.
			this.heat = Math.min(PASSIVE_CAP, this.heat + PASSIVE_HEAT_PER_TICK);
		}

		this.smeltTicks++;

		int smeltStage = Math.min(8, this.smeltTicks * 8 / SMELT_DURATION_TICKS);
		if (state.getValue(TataraFurnaceFiredBlock.SMELT_STAGE) != smeltStage) {
			level.setBlock(pos, state.setValue(TataraFurnaceFiredBlock.SMELT_STAGE, smeltStage), 3);
		}

		if (level instanceof ServerLevel serverLevel) {
			RandomSource random = level.getRandom();
			// Smoke steps up with each reddening stage - hotter and redder means more violent smoke.
			int smokeChanceOutOf100 = Math.min(95, 30 + smeltStage * 20);
			int smokeCount = 2 + smeltStage;
			if (random.nextInt(100) < smokeChanceOutOf100) {
				serverLevel.sendParticles(ParticleTypes.SMOKE,
						pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
						smokeCount, 0.2, 0.1, 0.2, 0.01);
			}
		}

		if (this.smeltTicks >= SMELT_DURATION_TICKS) {
			level.setBlock(pos, state.setValue(TataraFurnaceFiredBlock.KERA_FORMED, true).setValue(TataraFurnaceFiredBlock.SMELT_STAGE, 8), 3);
			level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.6F);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("SmeltTicks", this.smeltTicks);
		output.putFloat("Heat", this.heat);
		output.putLong("LastBellows", this.lastBellowsGameTime);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.smeltTicks = input.getIntOr("SmeltTicks", 0);
		this.heat = input.getFloatOr("Heat", 0.0F);
		this.lastBellowsGameTime = input.getLongOr("LastBellows", Long.MIN_VALUE / 2);
	}
}
