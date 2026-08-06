package net.scarabx.twoheavens.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.custom.KeraBlock;

public class KeraBlockEntity extends BlockEntity {

	public static final int COOL_DURATION_TICKS = 480; // 24 seconds

	private int coolTicks = 0;

	public KeraBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.KERA, pos, state);
	}

	public void serverTick(Level level, BlockPos pos, BlockState state) {
		if (this.coolTicks >= COOL_DURATION_TICKS) {
			return;
		}

		this.coolTicks++;
		int coolStage = Math.min(8, this.coolTicks * 8 / COOL_DURATION_TICKS);
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
