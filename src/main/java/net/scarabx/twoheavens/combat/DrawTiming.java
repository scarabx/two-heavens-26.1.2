package net.scarabx.twoheavens.combat;

/**
 * Shared draw/sheathe item-swap timing, in ticks from the R-press, matched to
 * draw_swords.animation.json's right_arm/left_arm arc keyframes (20
 * ticks/second). Used by BOTH the client's local visual prediction
 * (SwordDrawController) and the server's authoritative item swap
 * (SwordDrawServerHandler) - they must stay in sync, since the server's real
 * inventory change is what the client actually renders once it syncs back,
 * regardless of what the client's own local animation timing intended.
 */
public final class DrawTiming {

	public static final int DRAW_KATANA_DELAY_TICKS = 11;
	public static final int DRAW_WAKIZASHI_DELAY_TICKS = 14;

	public static final int SHEATHE_WAKIZASHI_DELAY_TICKS = 6;
	public static final int SHEATHE_KATANA_DELAY_TICKS = 17;

	private DrawTiming() {
	}
}
