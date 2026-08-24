package net.scarabx.twoheavens.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.custom.DaishoObiItem;

public class DaishoObiModel extends GeoModel<DaishoObiItem> {

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return TwoHeavens.id("daisho_obi");
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TwoHeavens.id("textures/item/daisho_obi.png");
	}

	@Override
	public Identifier getAnimationResource(DaishoObiItem animatable) {
		return TwoHeavens.id("daisho_obi");
	}
}
