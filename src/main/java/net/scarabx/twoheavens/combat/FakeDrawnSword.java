package net.scarabx.twoheavens.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.scarabx.twoheavens.item.ModItems;

/**
 * The fake katana/wakizashi swapped into a player's hands while drawn are
 * otherwise indistinguishable from a real katana/wakizashi - a plain
 * `new ItemStack(ModItems.KATANA)` looks exactly like one the player actually
 * owns. This marker (a CustomData component) lets sheathe find and strip
 * every fake stack out of the WHOLE inventory, not just mainhand/offhand, so a
 * fake sword doesn't get stranded (and duplicated/lost) if the player managed
 * to move it to another slot before sheathing.
 */
public final class FakeDrawnSword {

	private static final String MARKER_KEY = "twoheavens_fake_drawn_sword";

	private FakeDrawnSword() {
	}

	public static ItemStack katana() {
		return marked(new ItemStack(ModItems.KATANA));
	}

	public static ItemStack wakizashi() {
		return marked(new ItemStack(ModItems.WAKIZASHI));
	}

	public static boolean isFake(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
	}

	private static ItemStack marked(ItemStack stack) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARKER_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}
}
