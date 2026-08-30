package net.scarabx.twoheavens.combat;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.scarabx.twoheavens.item.ModItems;

import java.util.Optional;

/**
 * What the worn Daisho Obi is currently holding, and which swords in the world
 * came out of one.
 *
 * Replaces the old phantom scheme, where drawing FABRICATED a pair and sheathing
 * deleted them again. That is what made duplication possible at all: the swords in
 * your hands were copies, so anything you did with them left the originals intact
 * to be handed back. Every guard against it - the crafting mixin, the slot guard,
 * the entity guard - existed only to stop a copy being spent.
 *
 * Now the obi ISSUES its swords and knows they are gone. Draw takes them out and
 * the obi records it; sheathe puts back whichever ones came home. Leave a katana in
 * a chest and the obi simply stays short one - you have a sword and an obi missing
 * it, which is the truth, and nothing was created. **The dupe is not guarded
 * against, it is impossible**, which is why those three guards are deleted rather
 * than kept as belt and braces.
 *
 * Two tags, both CustomData:
 *
 *   on the obi     KATANA_OUT / WAKIZASHI_OUT - absent means the obi HOLDS it
 *   on a sword     FROM_OBI - this sword belongs to an obi and can go back
 *
 * Absent-means-held is what makes migration free. Every Daisho Obi crafted under
 * 1.0.0 carries no tags at all, so it reads as full, which is exactly right - those
 * obis were fabricating a pair on demand, so a pair is what they owe.
 *
 * The sword tag deliberately KEEPS the old NBT key. Anyone logged out mid-draw when
 * they update still has marked swords in their hands, and changing the string would
 * strand those - they would stop being recognised and become ordinary swords.
 */
public final class ObiSwords {

	private static final String FROM_OBI_KEY = "twoheavens_fake_drawn_sword";
	private static final String KATANA_OUT_KEY = "twoheavens_katana_out";
	private static final String WAKIZASHI_OUT_KEY = "twoheavens_wakizashi_out";

	private ObiSwords() {
	}

	/** A sword the obi issued. Ordinary swords are untouched by all of this. */
	public static boolean isFromObi(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(FROM_OBI_KEY, false);
	}

	public static ItemStack issuedKatana() {
		return tagged(new ItemStack(ModItems.KATANA));
	}

	public static ItemStack issuedWakizashi() {
		return tagged(new ItemStack(ModItems.WAKIZASHI));
	}

	/** Strips the ownership tag, so a sword taken out of the system reads as ordinary. */
	public static ItemStack untag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return stack;
		}
		CompoundTag tag = data.copyTag();
		tag.remove(FROM_OBI_KEY);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static ItemStack tagged(ItemStack stack) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(FROM_OBI_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The worn obi, if there is one. Empty when none is equipped. */
	public static Optional<TrinketSlotAccess> wornObi(Player player) {
		return TrinketsApi.getAttachment(player).findFirst(stack -> stack.is(ModItems.DAISHO_OBI));
	}

	public static boolean holds(Player player, Item sword) {
		return wornObi(player).map(slot -> !isOut(slot.get(), sword)).orElse(false);
	}

	/** Records that a sword has left the obi, or come back to it. */
	public static void setOut(Player player, Item sword, boolean out) {
		wornObi(player).ifPresent(slot -> {
			ItemStack obi = slot.get();
			if (obi.isEmpty()) {
				return;
			}
			CustomData data = obi.get(DataComponents.CUSTOM_DATA);
			CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
			String key = keyFor(sword);
			if (out) {
				tag.putBoolean(key, true);
			} else {
				// Removed rather than set false, so a full obi carries no tags at all -
				// the same shape a freshly crafted one has, and the same shape every obi
				// from 1.0.0 has. One representation for "full", not two.
				tag.remove(key);
			}
			obi.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			slot.set(obi);
		});
	}

	private static boolean isOut(ItemStack obi, Item sword) {
		if (obi.isEmpty()) {
			return false;
		}
		CustomData data = obi.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(keyFor(sword), false);
	}

	private static String keyFor(Item sword) {
		return sword == ModItems.KATANA ? KATANA_OUT_KEY : WAKIZASHI_OUT_KEY;
	}
}
