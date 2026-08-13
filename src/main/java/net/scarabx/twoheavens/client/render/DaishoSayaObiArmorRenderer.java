package net.scarabx.twoheavens.client.render;

import com.geckolib.renderer.GeoArmorRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.scarabx.twoheavens.item.custom.DaishoSayaObiItem;

public class DaishoSayaObiArmorRenderer extends GeoArmorRenderer<DaishoSayaObiItem, HumanoidRenderState> {

	public DaishoSayaObiArmorRenderer() {
		super(new DaishoSayaObiModel());
	}
}
