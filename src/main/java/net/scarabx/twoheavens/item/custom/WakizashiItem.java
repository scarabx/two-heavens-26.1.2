package net.scarabx.twoheavens.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.scarabx.twoheavens.item.ItemRecipeTooltip;
import java.util.Optional;
import java.util.function.Consumer;

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

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> consumer, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, consumer, flag);
		// Names the Daisho Obi rather than the R key on purpose: this is the first
		// place a player meets "draw", and pressing R without an obi does nothing.
		// The obi's own tooltip teaches the key, so neither repeats the other.
		consumer.accept(Component.translatable("tooltip.twoheavens.wakizashi_combat"));
		// Second line, split by STATE rather than by ability: line one carries no
		// condition so it reads as always true, and the obi appears only here.
		consumer.accept(Component.translatable("tooltip.twoheavens.wakizashi_drawn"));
		ItemRecipeTooltip.appendPrompt(stack, consumer);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return ItemRecipeTooltip.image(stack);
	}
}
