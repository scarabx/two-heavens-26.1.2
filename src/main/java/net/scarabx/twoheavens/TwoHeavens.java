package net.scarabx.twoheavens;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;
import net.scarabx.twoheavens.block.ModBlockEntities;
import net.scarabx.twoheavens.block.ModBlocks;
import net.scarabx.twoheavens.combat.SwordComboHandler;
import net.scarabx.twoheavens.combat.SwordDrawServerHandler;
import net.scarabx.twoheavens.event.AnvilForgingHandler;
import net.scarabx.twoheavens.item.ModItemGroups;
import net.scarabx.twoheavens.item.ModItems;
import net.scarabx.twoheavens.worldgen.ModWorldGeneration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwoHeavens implements ModInitializer {
	public static final String MOD_ID = "twoheavens";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		ModBlocks.registerModBlocks();
		ModBlockEntities.registerModBlockEntities();
		ModItems.registerModItems();
		ModItemGroups.registerItemGroups();
		ModWorldGeneration.registerWorldGeneration();
		AnvilForgingHandler.register();
		SwordComboHandler.register();
		SwordDrawServerHandler.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
