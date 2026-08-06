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

	public static final int SMELT_DURATION_TICKS = 2400; // 2 minutes
	private static final int BELLOWS_BOOST_WINDOW = 100; // 5s
	private static final int BELLOWS_STAGNATE_WINDOW = 400; // up to 20s
	private static final float HEAT_CHANGE_PER_TICK = 0.1F; // tuned for the 2-minute official duration
	private static final float MAX_HEAT = 100.0F;

	private int smeltTicks = 0;
	private float heat = 0.0F;
	private long lastBellowsGameTime = Long.MIN_VALUE / 2;

	public TataraFurnaceFiredBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TATARA_FURNACE_FIRED, pos, state);
	}

	public void startSmelting() {
		this.smeltTicks = 0;
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
		if (!state.getValue(TataraFurnaceFiredBlock.LIT) || state.getValue(TataraFurnaceFiredBlock.KERA_FORMED)) {
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
			// Fire died from neglect - reset the whole smelting attempt.
			level.setBlock(pos, state.setValue(TataraFurnaceFiredBlock.LIT, false)
					.setValue(TataraFurnaceFiredBlock.CHARCOAL_LEVEL, 0)
					.setValue(TataraFurnaceFiredBlock.SATETSU_LEVEL, 0)
					.setValue(TataraFurnaceFiredBlock.SMELT_STAGE, 0), 3);
			this.smeltTicks = 0;
			this.heat = 0.0F;
			this.setChanged();
			return;
		}

		this.smeltTicks++;

		int smeltStage = Math.min(8, this.smeltTicks * 8 / SMELT_DURATION_TICKS);
		if (state.getValue(TataraFurnaceFiredBlock.SMELT_STAGE) != smeltStage) {
			level.setBlock(pos, state.setValue(TataraFurnaceFiredBlock.SMELT_STAGE, smeltStage), 3);
		}

		if (level instanceof ServerLevel serverLevel) {
			RandomSource random = level.getRandom();
			// Smoke ramps up the further along smelting is, not just with instantaneous heat -
			// this is also the visual cue for how much charcoal remains under the satetsu.
			int smokeChanceOutOf100 = Math.min(95, 15 + smeltStage * 10 + (int) (this.heat / 5.0F));
			int smokeCount = 1 + smeltStage / 2;
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
