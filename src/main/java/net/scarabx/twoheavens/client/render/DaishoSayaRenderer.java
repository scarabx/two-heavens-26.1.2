package net.scarabx.twoheavens.client.render;

import com.geckolib.renderer.GeoItemRenderer;
import net.scarabx.twoheavens.item.custom.DaishoSayaItem;

public class DaishoSayaRenderer extends GeoItemRenderer<DaishoSayaItem> {

	public DaishoSayaRenderer() {
		super(new DaishoSayaModel());
	}
}
