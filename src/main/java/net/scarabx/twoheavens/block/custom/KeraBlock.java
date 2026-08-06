package net.scarabx.twoheavens.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.entity.KeraBlockEntity;
import org.jspecify.annotations.Nullable;

public class KeraBlock extends Block implements EntityBlock {

	public static final IntegerProperty COOL_STAGE = IntegerProperty.create("cool_stage", 0, 8);

	public KeraBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(COOL_STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COOL_STAGE);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KeraBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return type == ModBlockEntities.KERA
				? (lvl, pos, st, be) -> ((KeraBlockEntity) be).serverTick(lvl, pos, st)
				: null;
	}
}
