package net.scarabx.twoheavens.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-{@link ItemDisplayContext} position/rotation/scale nudges for GeckoLib item renders.
 * Edit the numbers in each renderer's ENTRIES table, rebuild, and look in-game — nothing
 * else here needs to be touched.
 *
 * offsetX/Y/Z: in blocks. 1/16 = one "pixel" at normal block-item scale.
 * rotX/Y/Z: degrees.
 * scale: multiplier, 1.0 = no change.
 */
public final class ItemPoseTuning {

	public record Entry(float offsetX, float offsetY, float offsetZ,
						 float rotX, float rotY, float rotZ,
						 float scale) {
		public static final Entry IDENTITY = new Entry(0, 0, 0, 0, 0, 0, 1f);
	}

	public static Map<ItemDisplayContext, Entry> table() {
		return new EnumMap<>(ItemDisplayContext.class);
	}

	/**
	 * Cancels the {@code translate(0.5F, 0.51F, 0.5F)} that {@link com.geckolib.renderer.GeoItemRenderer}
	 * applies in its own {@code adjustRenderPose}. That nudge shifts the geo mesh +8/+8.16/+8 model units,
	 * so geo coordinates would otherwise land half a block off from the identical coordinates in the
	 * item model's {@code elements}. Undoing it makes geo space and item-model space line up 1:1, which
	 * is what the hand-authored {@code display} transforms are built against.
	 */
	public static void cancelGeckolibCentering(PoseStack poseStack) {
		poseStack.translate(-0.5f, -0.51f, -0.5f);
	}

	public static void apply(PoseStack poseStack, ItemDisplayContext context, Map<ItemDisplayContext, Entry> entries) {
		Entry e = entries.getOrDefault(context, Entry.IDENTITY);
		if (e.offsetX != 0 || e.offsetY != 0 || e.offsetZ != 0) {
			poseStack.translate(e.offsetX, e.offsetY, e.offsetZ);
		}
		if (e.rotX != 0) poseStack.mulPose(Axis.XP.rotationDegrees(e.rotX));
		if (e.rotY != 0) poseStack.mulPose(Axis.YP.rotationDegrees(e.rotY));
		if (e.rotZ != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(e.rotZ));
		if (e.scale != 1f) poseStack.scale(e.scale, e.scale, e.scale);
	}
}
