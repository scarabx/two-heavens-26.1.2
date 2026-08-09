package net.scarabx.twoheavens.event;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.scarabx.twoheavens.item.ModItems;
import net.scarabx.twoheavens.item.custom.HammerItem;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class AnvilForgingHandler {

	private static final String HIT_TAG_PREFIX = "twoheavens_kera_hits_";
	private static final String MARKER_TAG = "twoheavens_forge_display";
	private static final String NEEDS_TONGS_TAG = "twoheavens_needs_tongs";

	private static final int ORPHAN_CHECK_INTERVAL_TICKS = 20;

	public static void register() {
		UseBlockCallback.EVENT.register(AnvilForgingHandler::onUseBlock);
		PlayerBlockBreakEvents.AFTER.register(AnvilForgingHandler::onAnvilBroken);
		ServerTickEvents.END_LEVEL_TICK.register(AnvilForgingHandler::onLevelTick);
	}

	private static boolean isBareHotBlade(ItemStack stack) {
		return stack.is(ModItems.HOT_KATANA_BLADE) || stack.is(ModItems.HOT_WAKIZASHI_BLADE);
	}

	private static InteractionResult onQuenchWater(Player player, Level level, InteractionHand hand, BlockPos pos) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!isBareHotBlade(main) || !player.getOffhandItem().is(ModItems.TONGS)) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel serverLevel) {
			net.minecraft.world.item.Item quenched = main.is(ModItems.HOT_KATANA_BLADE) ? ModItems.KATANA_BLADE : ModItems.WAKIZASHI_BLADE;
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(quenched));
			player.getOffhandItem().hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
			serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_EXTINGUISH_FIRE,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	private static void onLevelTick(ServerLevel level) {
		// Fast, every-tick check: bare-handing a hot blade burns you instantly and yanks it away.
		for (ServerPlayer player : level.players()) {
			ItemStack main = player.getMainHandItem();
			if (isBareHotBlade(main) && !player.getOffhandItem().is(ModItems.TONGS)) {
				player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 1));
				player.hurt(level.damageSources().onFire(), 1.0F);

				ItemStack dropped = main.copy();
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				ItemEntity entity = player.drop(dropped, true, false);
				if (entity != null) {
					entity.setPickUpDelay(32767);
					entity.addTag(NEEDS_TONGS_TAG);
				}
			}
		}

		// Every tick: hand a tongs-tagged blade to any nearby player who's actually holding tongs.
		for (ItemEntity itemEntity : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class),
				new AABB(-30000000, level.getMinY(), -30000000, 30000000, level.getMaxY(), 30000000),
				e -> e.entityTags().contains(NEEDS_TONGS_TAG))) {
			for (ServerPlayer player : level.players()) {
				if (player.getOffhandItem().is(ModItems.TONGS) && player.distanceToSqr(itemEntity) <= 4.0) {
					ItemStack picked = itemEntity.getItem().copy();
					if (!player.getInventory().add(picked)) {
						continue;
					}
					itemEntity.discard();
					break;
				}
			}
		}

		if (level.getGameTime() % ORPHAN_CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		AABB worldBounds = new AABB(-30000000, level.getMinY(), -30000000, 30000000, level.getMaxY(), 30000000);
		for (Display.ItemDisplay display : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Display.ItemDisplay.class),
				worldBounds, d -> d.entityTags().contains(MARKER_TAG))) {
			BlockPos anvilPos = BlockPos.containing(display.getX(), display.getY() - 1.0, display.getZ());
			if (!(level.getBlockState(anvilPos).getBlock() instanceof AnvilBlock)) {
				dropOrDiscard(level, display);
			}
		}
	}

	private static void onAnvilBroken(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(state.getBlock() instanceof AnvilBlock) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}

		for (Display.ItemDisplay display : findKeraDisplays(serverLevel, pos)) {
			dropOrDiscard(serverLevel, display);
		}
	}

	private static void dropOrDiscard(Level level, Display.ItemDisplay display) {
		// If the anvil and the forge display lose contact (broken, exploded, pushed away),
		// never destroy progress outright - every phase's item is a real item now, so it
		// always drops as exactly whatever is currently displayed.
		level.addFreshEntity(new ItemEntity(level, display.getX(), display.getY(), display.getZ(), display.getItemStack().copy()));
		display.discard();
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		BlockPos pos = hitResult.getBlockPos();

		if (level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
			return onQuenchWater(player, level, hand, pos);
		}

		if (!(level.getBlockState(pos).getBlock() instanceof AnvilBlock)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);

		if (!(level instanceof ServerLevel serverLevel)) {
			// Still claim the interaction client-side so vanilla's GUI never opens for these cases.
			return isRelevant(stack) || !findKeraDisplaysClient(level, pos).isEmpty() ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		List<Display.ItemDisplay> existing = findKeraDisplays(serverLevel, pos);

		int resumeHits = hitsForItem(stack);
		if (resumeHits >= 0) {
			if (existing.isEmpty()) {
				Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, serverLevel);
				display.setItemStack(new ItemStack(stack.getItem()));
				// NONE means only our own runtime Transformation governs the pose - otherwise
				// a baked per-context transform from the model's own JSON (e.g. "fixed") gets
				// applied on top of ours, which is invisible on symmetric shapes like this cube
				// but produces a badly compounded orientation on asymmetric shapes like blades.
				display.setItemTransform(net.minecraft.world.item.ItemDisplayContext.NONE);
				display.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
				if (resumeHits > 0) {
					display.setTransformation(new Transformation(null, anvilFacingRotation(level, pos), null, null));
					display.addTag(HIT_TAG_PREFIX + resumeHits);
				}
				display.addTag(MARKER_TAG);
				serverLevel.addFreshEntity(display);

				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}

		if (existing.isEmpty()) {
			return InteractionResult.PASS;
		}

		Display.ItemDisplay display = existing.get(0);

		if (stack.isEmpty()) {
			int hitsAtPickup = currentHits(display);
			if (hitsAtPickup >= 5 && player.getOffhandItem().is(ModItems.TONGS)) {
				// Finished blade, pulled off with tongs - a real pickupable item, not a prop.
				ItemStack blade = display.getItemStack().copy();
				if (!player.getInventory().add(blade)) {
					player.drop(blade, false);
				}
				player.getOffhandItem().hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
				display.discard();
				return InteractionResult.SUCCESS;
			}

			// Blade phases (5-6) without tongs: nothing happens, bare hands can't safely
			// pull a hot blade off the anvil. Every other phase (Kera shrinking, Ingot,
			// Flat Ingot) gives back exactly whatever is currently displayed.
			if (hitsAtPickup < 5) {
				ItemStack pickedUp = display.getItemStack().copy();
				if (!player.getInventory().add(pickedUp)) {
					player.drop(pickedUp, false);
				}
				display.discard();
			}
			return InteractionResult.SUCCESS;
		}

		if (stack.getItem() instanceof HammerItem) {
			serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE,
					net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

			int hits = currentHits(display);
			if (hits < 2) {
				hits++;
				float scale = 1.0F / (1 << hits); // 1st hit -> 0.5, 2nd hit -> 0.25
				// The model's pivot is centered, not bottom-anchored - scaling around it
				// alone lifts the bottom off the anvil surface, so push it back down to compensate.
				float dropToStayGrounded = -(1.0F - scale) * 0.5F;
				display.setTransformation(new Transformation(
						new Vector3f(0.0F, dropToStayGrounded, 0.0F),
						null,
						new Vector3f(scale, scale, scale),
						null));
				display.addTag(HIT_TAG_PREFIX + hits);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			} else if (hits == 2) {
				display.setItemStack(new ItemStack(ModItems.TAMAHAGANE_INGOT));
				display.setTransformation(new Transformation(null, anvilFacingRotation(level, pos), null, null));
				display.addTag(HIT_TAG_PREFIX + 3);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			} else if (hits == 3) {
				display.setItemStack(new ItemStack(ModItems.FLAT_TAMAHAGANE_INGOT));
				display.setTransformation(new Transformation(null, anvilFacingRotation(level, pos), null, null));
				display.addTag(HIT_TAG_PREFIX + 4);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			} else if (hits == 4) {
				display.setItemStack(new ItemStack(ModItems.HOT_WAKIZASHI_BLADE));
				display.setTransformation(new Transformation(null, anvilFacingRotation(level, pos), null, null));
				display.addTag(HIT_TAG_PREFIX + 5);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			} else if (hits == 5) {
				display.setItemStack(new ItemStack(ModItems.HOT_KATANA_BLADE));
				display.setTransformation(new Transformation(null, anvilFacingRotation(level, pos), null, null));
				display.addTag(HIT_TAG_PREFIX + 6);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			}
			return InteractionResult.SUCCESS;
		}

		// Anything else while Kera sits on the anvil: block vanilla's GUI, do nothing else.
		return InteractionResult.SUCCESS;
	}

	private static Quaternionf anvilFacingRotation(Level level, BlockPos pos) {
		// The Ingot model is authored for FACING=SOUTH (long axis on Z); rotate to match
		// the anvil's actual facing, mirroring the same y-rotation values vanilla's own
		// anvil.json blockstate uses (south=0, west=90, north=180, east=270).
		Direction facing = level.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
		float degrees = switch (facing) {
			case WEST -> 90.0F;
			case NORTH -> 180.0F;
			case EAST -> 270.0F;
			default -> 0.0F;
		};
		return new Quaternionf().rotateY((float) Math.toRadians(degrees));
	}

	private static int hitsForItem(ItemStack stack) {
		if (stack.is(ModItems.MOLTEN_KERA)) {
			return 0;
		}
		if (stack.is(ModItems.TAMAHAGANE_INGOT)) {
			return 3;
		}
		if (stack.is(ModItems.FLAT_TAMAHAGANE_INGOT)) {
			return 4;
		}
		if (stack.is(ModItems.HOT_WAKIZASHI_BLADE)) {
			return 5;
		}
		if (stack.is(ModItems.HOT_KATANA_BLADE)) {
			return 6;
		}
		return -1;
	}

	private static boolean isRelevant(ItemStack stack) {
		return hitsForItem(stack) >= 0 || stack.isEmpty() || stack.getItem() instanceof HammerItem;
	}

	private static List<Display.ItemDisplay> findKeraDisplays(ServerLevel level, BlockPos pos) {
		AABB checkBox = new AABB(pos).inflate(0.6);
		return level.getEntitiesOfClass(Display.ItemDisplay.class, checkBox, d -> d.entityTags().contains(MARKER_TAG));
	}

	private static List<Display.ItemDisplay> findKeraDisplaysClient(Level level, BlockPos pos) {
		AABB checkBox = new AABB(pos).inflate(0.6);
		return level.getEntitiesOfClass(Display.ItemDisplay.class, checkBox, d -> d.entityTags().contains(MARKER_TAG));
	}

	private static int currentHits(Display.ItemDisplay display) {
		if (display.entityTags().contains(HIT_TAG_PREFIX + 6)) {
			return 6;
		}
		if (display.entityTags().contains(HIT_TAG_PREFIX + 5)) {
			return 5;
		}
		if (display.entityTags().contains(HIT_TAG_PREFIX + 4)) {
			return 4;
		}
		if (display.entityTags().contains(HIT_TAG_PREFIX + 3)) {
			return 3;
		}
		if (display.entityTags().contains(HIT_TAG_PREFIX + 2)) {
			return 2;
		}
		if (display.entityTags().contains(HIT_TAG_PREFIX + 1)) {
			return 1;
		}
		return 0;
	}
}
