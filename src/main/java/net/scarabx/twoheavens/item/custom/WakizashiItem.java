package net.scarabx.twoheavens.item.custom;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.scarabx.twoheavens.combat.SweepEffect;

public class WakizashiItem extends Item {

	// Flat damage applied manually here instead of via an ATTACK_DAMAGE
	// attribute (there's no attack-speed attribute on this item either, so
	// swing timing never scales it down - every hit deals exactly this much).
	private static final float DAMAGE = 7.0F;

	public WakizashiItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		Level level = attacker.level();

		if (attacker instanceof ServerPlayer serverPlayer) {
			mob.hurt(level.damageSources().playerAttack(serverPlayer), DAMAGE);
		} else {
			mob.hurt(level.damageSources().mobAttack(attacker), DAMAGE);
		}

		SweepEffect.playFromHit(attacker);

		if (attacker instanceof Player player) {
			player.getCooldowns().addCooldown(stack, 10);
		}
	}
}
