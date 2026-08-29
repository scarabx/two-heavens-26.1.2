package net.scarabx.twoheavens.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.entity.TataraFurnaceBlockEntity;
import org.jspecify.annotations.Nullable;

public class TataraFurnaceBlock extends Block implements EntityBlock {

	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static final IntegerProperty COLOR_STAGE = IntegerProperty.create("color_stage", 0, 8);
	public static final IntegerProperty CHARCOAL_LEVEL = IntegerProperty.create("charcoal_level", 0, 8);
	public static final IntegerProperty BURN_STAGE = IntegerProperty.create("burn_stage", 0, 8);

	public TataraFurnaceBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(LIT, false)
				.setValue(COLOR_STAGE, 0)
				.setValue(CHARCOAL_LEVEL, 0)
				.setValue(BURN_STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT, COLOR_STAGE, CHARCOAL_LEVEL, BURN_STAGE);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (state.getValue(LIT)) {
			return InteractionResult.PASS;
		}

		if (stack.is(Items.CHARCOAL) && state.getValue(CHARCOAL_LEVEL) < 8) {
			if (!level.isClientSide()) {
				int newLevel = state.getValue(CHARCOAL_LEVEL) + 1;
				level.setBlock(pos, state.setValue(CHARCOAL_LEVEL, newLevel), 3);
				stack.shrink(1);
				level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}

		// Satetsu belongs in the FIRED furnace, after this one has cured - this one never
		// takes it at any fill level. Without this the click fell through to PASS, and
		// vanilla's answer to right-clicking a block while holding one is to PLACE it,
		// so a player following the starter hint's "set it aside for the Tatara Furnace"
		// stuck a satetsu block on the side of their furnace instead. Doing something
		// surprising is worse than doing nothing.
		if (stack.is(ModBlocks.SATETSU_SAND.asItem())) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(
						Component.translatable("message.twoheavens.satetsu_after_curing")
								.withStyle(ChatFormatting.GOLD));
				level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.0F);
			}
			return InteractionResult.CONSUME;
		}

		if (stack.getItem() instanceof FlintAndSteelItem) {
			if (state.getValue(CHARCOAL_LEVEL) < 8) {
				// Not fully filled yet - consume the interaction so vanilla flint-and-steel
				// doesn't fall through and place a stray fire block nearby.
				return InteractionResult.CONSUME;
			}

			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(LIT, true).setValue(BURN_STAGE, 0), 3);
				level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());

				if (level.getBlockEntity(pos) instanceof TataraFurnaceBlockEntity blockEntity) {
					blockEntity.startCuring();
				}
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TataraFurnaceBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return type == ModBlockEntities.TATARA_FURNACE
				? (lvl, pos, st, be) -> ((TataraFurnaceBlockEntity) be).serverTick(lvl, pos, st)
				: null;
	}
}
