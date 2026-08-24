package net.scarabx.twoheavens.client.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

public class DaishoObiTrinketRenderer implements TrinketRenderer {

	// LevelRenderer.levelRenderState is private in vanilla; GeckoLib's own
	// access widener makes it public for GeckoLib's compiled code but Loom
	// doesn't transitively widen it for our own compile classpath, so reflect
	// instead of depending on GeckoLib's widener scope.
	private static final Field LEVEL_RENDER_STATE_FIELD;

	static {
		try {
			LEVEL_RENDER_STATE_FIELD = LevelRenderer.class.getDeclaredField("levelRenderState");
			LEVEL_RENDER_STATE_FIELD.setAccessible(true);
		} catch (NoSuchFieldException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static CameraRenderState currentCameraRenderState() {
		try {
			LevelRenderState levelRenderState =
					(LevelRenderState) LEVEL_RENDER_STATE_FIELD.get(Minecraft.getInstance().levelRenderer);
			return levelRenderState.cameraRenderState;
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void submit(ItemStack stack, TrinketSlotAccess slot, EntityModel<? extends LivingEntityRenderState> model,
			PoseStack poseStack, SubmitNodeCollector collector, int packedLight, LivingEntityRenderState state,
			float limbSwing, float limbSwingAmount) {
		if (!(model instanceof HumanoidModel<?> humanoidModel) || !(state instanceof HumanoidRenderState humanoidState)
				|| !(humanoidState instanceof GeoRenderState) || !(stack.getItem() instanceof GeoAnimatable animatable)) {
			return;
		}
		LivingEntity entity = slot.inventory().getAttachment().getEntity();

		this.renderArmor(humanoidModel, poseStack, collector, stack, entity, animatable, packedLight,
				(HumanoidRenderState & GeoRenderState) humanoidState);
	}

	// tryRenderGeoArmorPiece() silently no-ops unless the entity render state
	// already has pre-computed per-slot armor data, which GeckoLib only populates
	// automatically for items sitting in a REAL vanilla equipment slot - never for
	// a Trinkets-held stack. So instead of that helper, this replicates its
	// essential steps directly (fillRenderState + performRenderPass), bypassing
	// that gate, while still reusing GeckoLib's real armor body-fitting logic
	// (bones named "armorBody" auto-snap to the torso ModelPart) rather than
	// hand-tuned PoseStack offsets. performRenderPass needs the live camera
	// render state (a null here compiles fine but silently renders nothing) -
	// GeckoLib's own equivalent call pulls it from levelRenderer.levelRenderState,
	// a field GeckoLib's own access widener makes available to us too.
	private <R extends HumanoidRenderState & GeoRenderState> void renderArmor(HumanoidModel<?> model,
			PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, LivingEntity entity,
			GeoAnimatable animatable, int packedLight, R renderState) {
		GeoArmorRenderer rawRenderer = GeoRenderProvider.of(stack).getGeoArmorRenderer(stack, EquipmentSlot.CHEST);
		if (rawRenderer == null) {
			return;
		}

		GeoArmorRenderer.RenderData renderData = new GeoArmorRenderer.RenderData(stack, EquipmentSlot.CHEST, entity, model);
		@SuppressWarnings("unchecked")
		GeoRenderState filledState = rawRenderer.fillRenderState(animatable, renderData, renderState, 1.0F);
		rawRenderer.performRenderPass(filledState, poseStack, collector, currentCameraRenderState());
	}
}
