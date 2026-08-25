package net.scarabx.twoheavens.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Client-to-server: "I performed this move, aimed at this entity".
 *
 * One payload for every move, replacing the earlier split between a cut-only
 * packet and a sweep-only one. The client is the only thing that knows a move was
 * made at all: the undrawn swords suppress vanilla's attack entirely so no
 * callback fires, and even the drawn moves reach the server only when vanilla
 * already found an entity - which is exactly the case that fails for a chicken or
 * a spider below the crosshair.
 *
 * targetId is whatever was under the crosshair, or -1 for none; the server falls
 * back to acquiring a nearby target itself. Everything that matters - held item,
 * drawn state, reach, target validity - is re-checked server-side. The client is
 * trusted only for "I pressed the button, roughly there".
 */
public record MovePayload(int move, int targetId) implements CustomPacketPayload {

	public static final int STAB = 0;
	public static final int KATANA = 1;
	public static final int CUT = 2;

	public static final CustomPacketPayload.Type<MovePayload> TYPE =
			new CustomPacketPayload.Type<>(TwoHeavens.id("move"));

	public static final StreamCodec<ByteBuf, MovePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MovePayload::move,
			ByteBufCodecs.VAR_INT, MovePayload::targetId,
			MovePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
