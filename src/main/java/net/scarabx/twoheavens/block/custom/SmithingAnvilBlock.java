package net.scarabx.twoheavens.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmithingAnvilBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<SmithingAnvilBlock> CODEC = simpleCodec(SmithingAnvilBlock::new);

	// Matches the smithing_anvil_template.json geometry (south/z-axis orientation).
	private static final VoxelShape Z_AXIS_SHAPE = Shapes.or(
			Block.box(2, 0, 2, 14, 4, 14),
			Block.box(4, 4, 3, 12, 5, 13),
			Block.box(6, 5, 4, 10, 10, 12),
			Block.box(3, 10, 0, 13, 16, 16)
	);
	private static final VoxelShape X_AXIS_SHAPE = Shapes.or(
			Block.box(2, 0, 2, 14, 4, 14),
			Block.box(3, 4, 4, 13, 5, 12),
			Block.box(4, 5, 6, 12, 10, 10),
			Block.box(0, 10, 3, 16, 16, 13)
	);

	public SmithingAnvilBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<SmithingAnvilBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The model's long axis runs along Z, so pointing FACING straight at the player
		// presents the narrow 10-wide end. Turn it a quarter so the long side faces them,
		// which is also the side you actually work from.
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_AXIS_SHAPE : Z_AXIS_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}
}
