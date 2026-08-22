package net.scarabx.twoheavens.client.render;

import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.world.item.ItemDisplayContext;
import net.scarabx.twoheavens.item.custom.DaishoSayaObiItem;

import java.util.Map;

public class DaishoSayaObiRenderer extends GeoItemRenderer<DaishoSayaObiItem> {

	// EDIT THESE NUMBERS, then rebuild + restart the game to see the change.
	// offsetX/Y/Z are in blocks (1/16 = one pixel). rotX/Y/Z are in degrees. scale is a multiplier.
	private static final Map<ItemDisplayContext, ItemPoseTuning.Entry> TUNING = ItemPoseTuning.table();
	static {
		// TUNING.put(ItemDisplayContext.GUI, new ItemPoseTuning.Entry(0f, 0f, 0f, 0f, 0f, 0f, 1f));
		// TUNING.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new ItemPoseTuning.Entry(0.5f, 0f, 0f, 0f, 180f, 0f, 1f));
		// TUNING.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, new ItemPoseTuning.Entry(0.5f, 0f, 0f, 0f, 180f, 0f, 1f));
		// TUNING.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new ItemPoseTuning.Entry(0f, 0f, 0f, 0f, 0f, 0f, 1f));
		// TUNING.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, new ItemPoseTuning.Entry(0f, 0f, 0f, 0f, 0f, 0f, 1f));
		// TUNING.put(ItemDisplayContext.GROUND, new ItemPoseTuning.Entry(0f, 0f, 0f, 0f, 0f, 0f, 1f));
		// TUNING.put(ItemDisplayContext.FIXED, new ItemPoseTuning.Entry(0f, 0f, 0f, 0f, 0f, 0f, 1f));
	}

	public DaishoSayaObiRenderer() {
		super(new DaishoSayaObiModel());
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> pass) {
		super.adjustRenderPose(pass);
		ItemPoseTuning.cancelGeckolibCentering(pass.poseStack());
		ItemDisplayContext context = pass.renderState().getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
		if (context != null) {
			ItemPoseTuning.apply(pass.poseStack(), context, TUNING);
		}
	}
}
