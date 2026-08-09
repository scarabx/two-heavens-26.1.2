package net.scarabx.twoheavens.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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

	private int curingTicks = 0;

	public TataraFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TATARA_FURNACE, pos, state);
	}

	public void startCuring() {
		this.curingTicks = 0;
		this.setChanged();
	}

	public void serverTick(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(TataraFurnaceBlock.LIT)) {
			return;
		}

		this.curingTicks++;

		int burnStage = Math.min(8, this.curingTicks * 8 / CURING_DURATION_TICKS);
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
		}

		if (this.curingTicks >= CURING_DURATION_TICKS) {
			level.setBlockAndUpdate(pos, ModBlocks.TATARA_FURNACE_FIRED.defaultBlockState());
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("CuringTicks", this.curingTicks);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.curingTicks = input.getIntOr("CuringTicks", 0);
	}
}
