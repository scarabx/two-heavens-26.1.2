package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.HumanoidAnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.ModifierLayer;
import net.minecraft.world.entity.Avatar;

/**
 * Single call site for triggering hand/arm animations on a player, so future
 * features (sword draw, combat combos) don't each need to touch the Player
 * Animation Library's raw API directly.
 */
public class PlayerHandAnimator {

	public static void trigger(Avatar avatar, RawAnimation animation) {
		IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(avatar, TwoHeavensPlayerAnimation.HANDS_LAYER_ID);
		if (layer instanceof ModifierLayer<?> modifierLayer
				&& modifierLayer.getAnimation() instanceof HumanoidAnimationController controller) {
			controller.triggerAnimation(animation);
		}
	}
}
