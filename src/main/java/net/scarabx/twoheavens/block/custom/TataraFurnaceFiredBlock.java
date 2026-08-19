package net.scarabx.twoheavens.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.block.entity.TataraFurnaceFiredBlockEntity;
import net.scarabx.twoheavens.item.custom.HammerItem;
import org.jspecify.annotations.Nullable;

public class TataraFurnaceFiredBlock extends Block implements EntityBlock {

	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static final IntegerProperty CHARCOAL_LEVEL = IntegerProperty.create("charcoal_level", 0, 4);
	public static final IntegerProperty SATETSU_LEVEL = IntegerProperty.create("satetsu_level", 0, 4);
	// SMELT_STAGE is the layer HEIGHT - a high-water mark that only ever
	// climbs, never regrows down. REDNESS_STAGE is the current heat-driven
	// glow/color, which can dip back down without the layer height
	// following it - matches the geometry never un-melting while the color
	// still fades if it cools off.
	public static final IntegerProperty SMELT_STAGE = IntegerProperty.create("smelt_stage", 0, 8);
	public static final IntegerProperty REDNESS_STAGE = IntegerProperty.create("redness_stage", 0, 8);
	public static final BooleanProperty KERA_FORMED = BooleanProperty.create("kera_formed");
	public static final IntegerProperty CRACK_STAGE = IntegerProperty.create("crack_stage", 0, 4);

	public TataraFurnaceFiredBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(LIT, false)
				.setValue(CHARCOAL_LEVEL, 0)
				.setValue(SATETSU_LEVEL, 0)
				.setValue(SMELT_STAGE, 0)
				.setValue(REDNESS_STAGE, 0)
				.setValue(KERA_FORMED, false)
				.setValue(CRACK_STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT, CHARCOAL_LEVEL, SATETSU_LEVEL, SMELT_STAGE, REDNESS_STAGE, KERA_FORMED, CRACK_STAGE);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (state.getValue(KERA_FORMED)) {
			if (stack.getItem() instanceof HammerItem) {
				if (!level.isClientSide()) {
					int newCrack = state.getValue(CRACK_STAGE) + 1;
					level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
					stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
					if (newCrack >= 4) {
						level.levelEvent(null, 2001, pos, Block.getId(state));
						level.setBlockAndUpdate(pos, ModBlocks.KERA_BLOCK.defaultBlockState());
					} else {
						level.setBlock(pos, state.setValue(CRACK_STAGE, newCrack), 3);
					}
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		if (state.getValue(LIT)) {
			return InteractionResult.PASS;
		}

		if (state.getValue(CHARCOAL_LEVEL) < 4 && stack.is(Items.CHARCOAL)) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(CHARCOAL_LEVEL, state.getValue(CHARCOAL_LEVEL) + 1), 3);
				stack.shrink(1);
				level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}

		if (state.getValue(CHARCOAL_LEVEL) == 4 && state.getValue(SATETSU_LEVEL) < 4 && stack.is(ModBlocks.SATETSU_SAND.asItem())) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(SATETSU_LEVEL, state.getValue(SATETSU_LEVEL) + 1), 3);
				stack.shrink(1);
				level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}

		if (stack.getItem() instanceof FlintAndSteelItem) {
			if (state.getValue(CHARCOAL_LEVEL) < 4 || state.getValue(SATETSU_LEVEL) < 4) {
				// Not fully filled yet - consume the interaction so vanilla flint-and-steel
				// doesn't fall through and place a stray fire block nearby.
				return InteractionResult.CONSUME;
			}

			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(LIT, true).setValue(SMELT_STAGE, 0).setValue(REDNESS_STAGE, 0), 3);
				level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());

				if (level.getBlockEntity(pos) instanceof TataraFurnaceFiredBlockEntity blockEntity) {
					blockEntity.startSmelting();
				}
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TataraFurnaceFiredBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return type == ModBlockEntities.TATARA_FURNACE_FIRED
				? (lvl, pos, st, be) -> ((TataraFurnaceFiredBlockEntity) be).serverTick(lvl, pos, st)
				: null;
	}
}
