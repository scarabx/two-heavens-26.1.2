package net.scarabx.twoheavens.event;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.scarabx.twoheavens.block.custom.SmithingAnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.scarabx.twoheavens.item.ModItems;
import net.scarabx.twoheavens.item.custom.HammerItem;
import org.joml.Quaternionf;

import java.util.List;

public class AnvilForgingHandler {

	private static final String HIT_TAG_PREFIX = "twoheavens_kera_hits_";
	private static final String MARKER_TAG = "twoheavens_forge_display";

	private static final int ORPHAN_CHECK_INTERVAL_TICKS = 20;

	public static void register() {
		UseBlockCallback.EVENT.register(AnvilForgingHandler::onUseBlock);
		UseItemCallback.EVENT.register(AnvilForgingHandler::onUseItem);
		PlayerBlockBreakEvents.AFTER.register(AnvilForgingHandler::onAnvilBroken);
		ServerTickEvents.END_LEVEL_TICK.register(AnvilForgingHandler::onLevelTick);
	}

	/**
	 * The Smithing Anvil only. Vanilla anvils used to be accepted as a convenience,
	 * but it did not work reliably in play and the tooltip now names the Smithing
	 * Anvil - making it the single answer keeps the chain coherent, since the anvil
	 * is a step the player crafts rather than one they might stumble on in a village.
	 */
	private static boolean isForgeAnvil(Block block) {
		return block instanceof SmithingAnvilBlock;
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

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!isBareHotBlade(main) || !player.getOffhandItem().is(ModItems.TONGS)) {
			return InteractionResult.PASS;
		}

		// The vanilla block-use raycast ignores fluids by default (only bucket-style items
		// clip against them), so a plain UseBlockCallback never fires when aiming at open
		// water. Do our own fluid-inclusive clip here instead.
		Vec3 eyePos = player.getEyePosition(1.0F);
		Vec3 viewVec = player.getViewVector(1.0F);
		double reach = 5.0;
		Vec3 endPos = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);
		BlockHitResult hit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));

		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel serverLevel) {
			net.minecraft.world.item.Item quenched = main.is(ModItems.HOT_KATANA_BLADE) ? ModItems.KATANA_BLADE : ModItems.WAKIZASHI_BLADE;
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(quenched));
			player.getOffhandItem().hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
			serverLevel.playSound(null, hit.getBlockPos(), net.minecraft.sounds.SoundEvents.GENERIC_EXTINGUISH_FIRE,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	private static void onLevelTick(ServerLevel level) {
		// Fast, every-tick check: carrying a hot blade anywhere in the inventory without
		// tongs in the offhand burns you instantly and ejects every such blade.
		for (ServerPlayer player : level.players()) {
			if (player.getOffhandItem().is(ModItems.TONGS)) {
				continue;
			}

			boolean burned = false;
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!isBareHotBlade(stack)) {
					continue;
				}

				// A normal drop, pickable by anyone - a player without tongs who scoops it
				// back up just gets caught by this same loop again next tick and it bounces
				// right back out, so it still can't be kept without tongs.
				ItemStack dropped = stack.copy();
				inventory.setItem(slot, ItemStack.EMPTY);
				player.drop(dropped, true, false);
				burned = true;
			}

			if (burned) {
				player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 1));
				player.hurt(level.damageSources().onFire(), 1.0F);
			}
		}

		if (level.getGameTime() % ORPHAN_CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		AABB worldBounds = new AABB(-30000000, level.getMinY(), -30000000, 30000000, level.getMaxY(), 30000000);
		for (Display.ItemDisplay display : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Display.ItemDisplay.class),
				worldBounds, d -> d.entityTags().contains(MARKER_TAG))) {
			BlockPos anvilPos = BlockPos.containing(display.getX(), display.getY() - 1.0, display.getZ());
			if (!isForgeAnvil(level.getBlockState(anvilPos).getBlock())) {
				dropOrDiscard(level, display);
			}
		}
	}

	private static void onAnvilBroken(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!isForgeAnvil(state.getBlock()) || !(level instanceof ServerLevel serverLevel)) {
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

		if (!isForgeAnvil(level.getBlockState(pos).getBlock())) {
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

			// Blade phases (5-6) without tongs: bare hands can't safely pull a hot blade
			// off the anvil. Say so - this used to return SUCCESS having done nothing at
			// all, which is indistinguishable from the anvil being broken, and it is the
			// last step of the whole chain to get stuck on.
			if (hitsAtPickup >= 5) {
				player.sendOverlayMessage(
						Component.translatable("message.twoheavens.tongs_to_lift")
								.withStyle(ChatFormatting.GOLD));
				serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL,
						net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
			}

			// Every other phase (Kera, Ingot, Flat Ingot) gives back exactly whatever is
			// currently displayed.
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
			int hits = currentHits(display);

			// Past the katana there is nothing left to forge. The anvil sound used to
			// play regardless, so striking a finished blade sounded exactly like
			// progress - worse than silence, because it invites you to keep going.
			if (hits >= 6) {
				player.sendOverlayMessage(
						Component.translatable("message.twoheavens.tongs_to_lift")
								.withStyle(ChatFormatting.GOLD));
				serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL,
						net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE,
					net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

			// The Kera is already flat coming out of the furnace, so it no longer shrinks
			// across two hits first - the opening strike works it straight into an ingot.
			if (hits == 0) {
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
