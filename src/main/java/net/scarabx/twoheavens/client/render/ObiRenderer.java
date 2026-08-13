package net.scarabx.twoheavens.client.render;

import com.geckolib.renderer.GeoItemRenderer;
import net.scarabx.twoheavens.item.custom.ObiItem;

public class ObiRenderer extends GeoItemRenderer<ObiItem> {

	public ObiRenderer() {
		super(new ObiModel());
	}
}
