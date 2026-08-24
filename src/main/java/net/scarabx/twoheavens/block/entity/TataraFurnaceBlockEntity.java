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
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.block.custom.TataraFurnaceBlock;

public class TataraFurnaceBlockEntity extends BlockEntity {

	public static final int CURING_DURATION_TICKS = 1200; // 1 minute

	// No interaction here (no bellows equivalent for curing) - heat still
	// climbs in the same discrete-step style as the fired furnace's passive
	// phase, one step every 7.5s, purely off the clock. The burn/color
	// stage is derived from heat rather than directly from curingTicks, for
	// the same reason - it's what's actually "cooking" the furnace, time is
	// just what drives it up.
	private static final float MAX_HEAT = 100.0F;
	private static final int STEP_TICKS = CURING_DURATION_TICKS / 8; // 150 ticks = 7.5s
	private static final float STEP_HEAT = MAX_HEAT / 8.0F;

	private int curingTicks = 0;
	private float heat = 0.0F;

	public TataraFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TATARA_FURNACE, pos, state);
	}

	public void startCuring() {
		this.curingTicks = 0;
		this.heat = 0.0F;
		this.setChanged();
	}

	public void serverTick(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(TataraFurnaceBlock.LIT)) {
			return;
		}

		this.curingTicks++;

		int completedSteps = Math.min(8, this.curingTicks / STEP_TICKS);
		this.heat = Math.min(MAX_HEAT, STEP_HEAT * completedSteps);

		int burnStage = Math.max(0, Math.min(8, (int) (this.heat / STEP_HEAT)));
		int colorStage = burnStage;
		if (state.getValue(TataraFurnaceBlock.BURN_STAGE) != burnStage || state.getValue(TataraFurnaceBlock.COLOR_STAGE) != colorStage) {
			level.setBlock(pos, state.setValue(TataraFurnaceBlock.BURN_STAGE, burnStage).setValue(TataraFurnaceBlock.COLOR_STAGE, colorStage), 3);
		}

		if (level instanceof ServerLevel serverLevel) {
			RandomSource random = level.getRandom();
			// Smoke ramps up the further along curing is.
			int smokeChanceOutOf100 = Math.min(95, 15 + burnStage * 10);
			int smokeCount = 1 + burnStage / 2;
			if (random.nextInt(100) < smokeChanceOutOf100) {
				serverLevel.sendParticles(ParticleTypes.SMOKE,
						pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
						smokeCount, 0.2, 0.1, 0.2, 0.01);
			}

			// Quiet, intermittent crackle for the whole curing process - kept
			// low/infrequent on purpose so it never competes with an actual
			// event sound landing on top of it.
			if (random.nextInt(40) == 0) {
				level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
						0.5F, 0.8F + random.nextFloat() * 0.4F);
			}
		}

		if (this.curingTicks >= CURING_DURATION_TICKS) {
			// Fired ceramic settling, deliberately quieter than the kera's whoosh -
			// curing is a step, not the payoff, and the two should not read alike.
			level.playSound(null, pos, SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
			level.setBlockAndUpdate(pos, ModBlocks.TATARA_FURNACE_FIRED.defaultBlockState());
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("CuringTicks", this.curingTicks);
		output.putFloat("Heat", this.heat);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.curingTicks = input.getIntOr("CuringTicks", 0);
		this.heat = input.getFloatOr("Heat", 0.0F);
	}
}
