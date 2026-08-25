package net.scarabx.twoheavens.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * Client-to-server: "I swung the undrawn wakizashi at this entity".
 *
 * Needed because the undrawn wakizashi's left-click is intercepted before vanilla
 * ever runs its attack - which is what stops vanilla's own swing animation from
 * fighting ours - so AttackEntityCallback never fires and there is no other way
 * for the server to hear about the swing.
 *
 * Carries only the entity id the client was aiming at (-1 for a swing at air).
 * The server re-checks the held item, the drawn state and the range itself; the
 * id is a pointer, not a claim.
 */
public record WakizashiCutPayload(int targetId) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<WakizashiCutPayload> TYPE =
			new CustomPacketPayload.Type<>(TwoHeavens.id("wakizashi_cut"));

	public static final StreamCodec<ByteBuf, WakizashiCutPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WakizashiCutPayload::targetId,
			WakizashiCutPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
