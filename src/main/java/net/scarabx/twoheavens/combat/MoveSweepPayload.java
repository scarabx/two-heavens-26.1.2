package net.scarabx.twoheavens.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Client-to-server: "I performed one of the mod's moves, play its sweep".
 *
 * Every custom move gets the sweep sound and particles the instant it fires,
 * whether or not it connects - a swing at air reads as a swing, not as nothing.
 * That cannot be driven off hits or off vanilla's swing: the drawn moves reach
 * the server only when an entity is involved, and the undrawn ones suppress
 * vanilla's swing entirely so it can't fight our animation.
 *
 * Carries no data. The server plays the effect at the sender's own position and
 * has nothing to trust.
 */
public record MoveSweepPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<MoveSweepPayload> TYPE =
			new CustomPacketPayload.Type<>(TwoHeavens.id("move_sweep"));

	public static final StreamCodec<ByteBuf, MoveSweepPayload> CODEC =
			StreamCodec.unit(new MoveSweepPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
