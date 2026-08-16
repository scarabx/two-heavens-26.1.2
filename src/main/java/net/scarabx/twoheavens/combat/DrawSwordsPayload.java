package net.scarabx.twoheavens.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Client-to-server: "toggle drawn/sheathed" request. Carries no data - the
 * server independently re-checks Trinkets equip and current held items, it
 * never trusts client state for anything that affects real gameplay.
 */
public record DrawSwordsPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DrawSwordsPayload> TYPE =
			new CustomPacketPayload.Type<>(TwoHeavens.id("draw_swords"));

	public static final StreamCodec<ByteBuf, DrawSwordsPayload> CODEC =
			StreamCodec.unit(new DrawSwordsPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
