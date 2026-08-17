package net.scarabx.twoheavens.client.animation;

import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.HumanoidAnimationController;
import com.zigythebird.playeranimcore.animation.layered.ModifierLayer;
import com.zigythebird.playeranimcore.animation.layered.modifier.FirstPersonOffsetModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import net.minecraft.resources.Identifier;
import net.scarabx.twoheavens.TwoHeavens;
import team.unnamed.mocha.MochaEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Foundation for animating player hands/arms independently of vanilla's
 * held-item pose, using the Player Animation Library instead of vanilla
 * mixins. This registers one animation layer ("hands") per player that
 * future features (sword draw, combat combos) will drive - no real combat
 * animation exists yet, this is plumbing plus one visible test animation.
 */
public class TwoHeavensPlayerAnimation {

	public static final Identifier HANDS_LAYER_ID = TwoHeavens.id("hands");

	private static Map<String, Animation> testAnimations;
	private static Map<String, Animation> drawSwordsAnimations;
	private static Map<String, Animation> sheatheSwordsAnimations;
	private static Map<String, Animation> attackSwingAnimations;
	private static Map<String, Animation> attackSwingReturnAnimations;
	private static Map<String, Animation> katanaSliceAnimations;
	private static Map<String, Animation> katanaSliceReturnAnimations;
	private static Map<String, Animation> combatIdleAnimations;

	// Animations are loaded once and cached (below), so an F3+T resource
	// reload alone won't pick up edited animation JSON - this clears the
	// cache so F3+T is enough during iteration, no full game restart needed.
	public static void clearCache() {
		testAnimations = null;
		drawSwordsAnimations = null;
		sheatheSwordsAnimations = null;
		attackSwingAnimations = null;
		attackSwingReturnAnimations = null;
		katanaSliceAnimations = null;
		katanaSliceReturnAnimations = null;
		combatIdleAnimations = null;
	}

	public static void register() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HANDS_LAYER_ID, 1000, avatar -> {
			HumanoidAnimationController controller = new HumanoidAnimationController(
					(ctrl, data, setter) -> PlayState.CONTINUE,
					MochaEngine::createStandard);
			// Defaults to off (no first-person rendering at all) unless set
			// explicitly - THIRD_PERSON_MODEL renders our animated arm model
			// in first person too, instead of vanilla's separate hardcoded
			// first-person arms.
			controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
			// Default FirstPersonConfiguration has both arms hidden (only items
			// are shown by default) - without this, arms never render in first
			// person no matter what the animation itself does.
			controller.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true, true));
			// Without this, arms stay at their third-person world position instead
			// of tracking the first-person camera as it looks around - meaning
			// they essentially never end up in view regardless of the animation's
			// own rotation values. Enabling it is documented to push arms too low,
			// which is what FirstPersonOffsetModifier corrects for.
			controller.setFirstPersonFollowsCamera(true);
			controller.addModifierLast(new FirstPersonOffsetModifier());
			return new ModifierLayer<>(controller);
		});
	}

	public static Animation getTestWaveAnimation() {
		if (testAnimations == null) {
			testAnimations = loadAnimations("test_wave.animation.json");
		}
		return testAnimations.get("animation.twoheavens.test_wave");
	}

	public static Animation getDrawSwordsAnimation() {
		if (drawSwordsAnimations == null) {
			drawSwordsAnimations = loadAnimations("draw_swords.animation.json");
		}
		return drawSwordsAnimations.get("animation.twoheavens.draw_swords");
	}

	public static Animation getSheatheSwordsAnimation() {
		if (sheatheSwordsAnimations == null) {
			sheatheSwordsAnimations = loadAnimations("sheathe_swords.animation.json");
		}
		return sheatheSwordsAnimations.get("animation.twoheavens.sheathe_swords");
	}

	public static Animation getAttackSwingAnimation() {
		if (attackSwingAnimations == null) {
			attackSwingAnimations = loadAnimations("attack_swing.animation.json");
		}
		return attackSwingAnimations.get("animation.twoheavens.attack_swing");
	}

	public static Animation getAttackSwingReturnAnimation() {
		if (attackSwingReturnAnimations == null) {
			attackSwingReturnAnimations = loadAnimations("attack_swing_return.animation.json");
		}
		return attackSwingReturnAnimations.get("animation.twoheavens.attack_swing_return");
	}

	public static Animation getKatanaSliceAnimation() {
		if (katanaSliceAnimations == null) {
			katanaSliceAnimations = loadAnimations("katana_slice.animation.json");
		}
		return katanaSliceAnimations.get("animation.twoheavens.katana_slice");
	}

	public static Animation getKatanaSliceReturnAnimation() {
		if (katanaSliceReturnAnimations == null) {
			katanaSliceReturnAnimations = loadAnimations("katana_slice_return.animation.json");
		}
		return katanaSliceReturnAnimations.get("animation.twoheavens.katana_slice_return");
	}

	public static Animation getCombatIdleAnimation() {
		if (combatIdleAnimations == null) {
			combatIdleAnimations = loadAnimations("combat_idle.animation.json");
		}
		return combatIdleAnimations.get("animation.twoheavens.combat_idle");
	}

	private static Map<String, Animation> loadAnimations(String fileName) {
		try (InputStream stream = TwoHeavensPlayerAnimation.class.getResourceAsStream(
				"/assets/twoheavens/player_animations/" + fileName)) {
			return UniversalAnimLoader.loadAnimations(stream);
		} catch (IOException exception) {
			throw new RuntimeException("Failed to load " + fileName, exception);
		}
	}
}
