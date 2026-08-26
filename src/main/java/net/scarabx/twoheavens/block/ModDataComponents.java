package net.scarabx.twoheavens.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.scarabx.twoheavens.TwoHeavens;

/**
 * How far along a furnace was when it was broken, carried on the dropped item so
 * putting it back down resumes rather than restarts.
 *
 * Ticks and heat cannot live in the block state - a state property would need 1200
 * values - so this is a component instead, collected from the block entity by the
 * loot table's copy_components and re-applied when the block is placed.
 */
public final class ModDataComponents {

	public record FurnaceProgress(int ticks, float heat) {

		public static final Codec<FurnaceProgress> CODEC = RecordCodecBuilder.create(instance ->
				instance.group(
						Codec.INT.fieldOf("ticks").forGetter(FurnaceProgress::ticks),
						Codec.FLOAT.fieldOf("heat").forGetter(FurnaceProgress::heat)
				).apply(instance, FurnaceProgress::new));
	}

	public static final DataComponentType<FurnaceProgress> FURNACE_PROGRESS =
			Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TwoHeavens.id("furnace_progress"),
					DataComponentType.<FurnaceProgress>builder().persistent(FurnaceProgress.CODEC).build());

	private ModDataComponents() {
	}

	/** Forces registration during mod init, like the attachments do. */
	public static void touch() {
	}
}
