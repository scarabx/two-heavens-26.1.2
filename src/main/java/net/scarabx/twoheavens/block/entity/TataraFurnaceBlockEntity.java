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

	public static final int CURING_DURATION_TICKS = 2400; // 2 minutes
	private static final int BELLOWS_BOOST_WINDOW = 100; // 5s: recently bellowed -> heat rises
	private static final int BELLOWS_STAGNATE_WINDOW = 400; // up to 20s since bellows -> heat holds
	private static final float HEAT_CHANGE_PER_TICK = 0.1F; // ~2 heat per second, tuned for the 2-minute official duration
	private static final float MAX_HEAT = 100.0F;

	private int curingTicks = 0;
	private float heat = 0.0F;
	private long lastBellowsGameTime = Long.MIN_VALUE / 2;

	public TataraFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TATARA_FURNACE, pos, state);
	}

	public void startCuring() {
		this.curingTicks = 0;
		this.heat = 50.0F;
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
		if (!state.getValue(TataraFurnaceBlock.LIT)) {
			return;
		}

		long ticksSinceBellows = level.getGameTime() - this.lastBellowsGameTime;
		if (ticksSinceBellows < BELLOWS_BOOST_WINDOW) {
			this.heat = Math.min(MAX_HEAT, this.heat + HEAT_CHANGE_PER_TICK);
		} else if (ticksSinceBellows < BELLOWS_STAGNATE_WINDOW) {
			// heat holds steady
		} else {
			this.heat = Math.max(0.0F, this.heat - HEAT_CHANGE_PER_TICK);
		}

		if (this.heat <= 0.0F) {
			// The fire has died from neglect - reset entirely.
			level.setBlock(pos, state.setValue(TataraFurnaceBlock.LIT, false)
					.setValue(TataraFurnaceBlock.CHARCOAL_LEVEL, 0)
					.setValue(TataraFurnaceBlock.COLOR_STAGE, 0)
					.setValue(TataraFurnaceBlock.BURN_STAGE, 0), 3);
			this.curingTicks = 0;
			this.heat = 0.0F;
			this.setChanged();
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
			// Smoke ramps up the further along curing is, not just with instantaneous heat.
			int smokeChanceOutOf100 = Math.min(95, 15 + burnStage * 10 + (int) (this.heat / 5.0F));
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
		output.putFloat("Heat", this.heat);
		output.putLong("LastBellows", this.lastBellowsGameTime);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.curingTicks = input.getIntOr("CuringTicks", 0);
		this.heat = input.getFloatOr("Heat", 0.0F);
		this.lastBellowsGameTime = input.getLongOr("LastBellows", Long.MIN_VALUE / 2);
	}
}
