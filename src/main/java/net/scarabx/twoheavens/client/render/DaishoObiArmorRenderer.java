package net.scarabx.twoheavens.client.render;

import com.geckolib.renderer.GeoArmorRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.scarabx.twoheavens.item.custom.DaishoObiItem;

public class DaishoObiArmorRenderer extends GeoArmorRenderer<DaishoObiItem, HumanoidRenderState> {

	public DaishoObiArmorRenderer() {
		super(new DaishoObiModel());
	}
}
