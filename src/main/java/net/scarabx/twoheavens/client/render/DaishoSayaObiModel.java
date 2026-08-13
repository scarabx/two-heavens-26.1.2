package net.scarabx.twoheavens.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import net.scarabx.twoheavens.TwoHeavens;
import net.scarabx.twoheavens.item.custom.DaishoSayaObiItem;

public class DaishoSayaObiModel extends GeoModel<DaishoSayaObiItem> {

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return TwoHeavens.id("daisho_saya_obi");
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TwoHeavens.id("textures/item/daisho_saya_obi.png");
	}

	@Override
	public Identifier getAnimationResource(DaishoSayaObiItem animatable) {
		return TwoHeavens.id("daisho_saya_obi");
	}
}
