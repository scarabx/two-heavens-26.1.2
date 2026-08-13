package net.scarabx.twoheavens.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.custom.ObiItem;

public class ObiModel extends GeoModel<ObiItem> {

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return TwoHeavens.id("obi");
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TwoHeavens.id("textures/item/obi.png");
	}

	@Override
	public Identifier getAnimationResource(ObiItem animatable) {
		return TwoHeavens.id("obi");
	}
}
