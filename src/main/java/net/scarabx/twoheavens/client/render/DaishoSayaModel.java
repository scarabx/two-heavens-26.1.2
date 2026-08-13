package net.scarabx.twoheavens.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.custom.DaishoSayaItem;

public class DaishoSayaModel extends GeoModel<DaishoSayaItem> {

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return TwoHeavens.id("daisho_saya");
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TwoHeavens.id("textures/item/daisho_saya.png");
	}

	@Override
	public Identifier getAnimationResource(DaishoSayaItem animatable) {
		return TwoHeavens.id("daisho_saya");
	}
}
