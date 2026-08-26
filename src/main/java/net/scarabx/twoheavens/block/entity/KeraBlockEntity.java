package net.scarabx.twoheavens.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.block.custom.KeraBlock;

public class KeraBlockEntity extends BlockEntity {

	// 10 seconds, down from 24. The kera cannot be broken while it cools, and this
	// lands straight after a 60-second smelt - so it was a long second wait with
	// nothing to press, right at the payoff. Long enough to watch the stages play out,
	// short enough that nobody wanders off.
	public static final int COOL_DURATION_TICKS = 200;

	private int coolTicks = 0;

	public KeraBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.KERA, pos, state);
	}

	public void serverTick(Level level, BlockPos pos, BlockState state) {
		if (this.coolTicks >= COOL_DURATION_TICKS) {
			return;
		}

		this.coolTicks++;

		if (this.coolTicks >= COOL_DURATION_TICKS) {
			// Fully cooled - becomes the separate static Cold Kera block.
			level.setBlock(pos, ModBlocks.KERA.defaultBlockState(), 3);
			return;
		}

		int coolStage = Math.min(7, this.coolTicks * 8 / COOL_DURATION_TICKS);
		if (state.getValue(KeraBlock.COOL_STAGE) != coolStage) {
			level.setBlock(pos, state.setValue(KeraBlock.COOL_STAGE, coolStage), 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("CoolTicks", this.coolTicks);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.coolTicks = input.getIntOr("CoolTicks", 0);
	}
}
