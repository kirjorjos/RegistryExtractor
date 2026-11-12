package com.kirjorjos.registryextractor;

import net.minecraft.network.chat.Component;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.fml.ModList;

public class ExtractCommand {

	/**
	 * The minecraft version this build declares in its own {@code mods.toml} dependency range.
	 *
	 * <p>Read from the mod's own metadata rather than declared a second time, so there is one source of
	 * truth and a version branch cannot export a plausible-looking lie. There is deliberately no fallback:
	 * if this range is missing or unreadable that is a bug in the metadata, not a state to render.
	 */
	private static String gameVersion() {
		return ModList.get().getModContainerById(RegistryExtractor.MOD_ID)
				.orElseThrow(() -> new IllegalStateException(
						"Registry Extractor is missing from the mod list, so gameVersion cannot be derived"))
				.getModInfo().getDependencies().stream()
				.filter(dependency -> "minecraft".equals(dependency.getModId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"mods.toml declares no minecraft dependency, so gameVersion cannot be derived"))
				.getVersionRange().getRestrictions().get(0).getLowerBound().toString();
	}

	@SuppressWarnings("null")
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
			dispatcher.register(
					Commands.literal("extract")
							.then(
									Commands.argument("args", StringArgumentType.greedyString())
											.executes(context -> {

												JsonObject root = new JsonObject();

												// Create the "metadata" object
												JsonObject metadata = new JsonObject();
												metadata.addProperty("schemaVersion", "1");
												metadata.addProperty("gameVersion", gameVersion());
												metadata.addProperty("description", "Generated game registries for WIP Integrated Dynamics project");

												// Add the metadata object to the root
												root.add("metadata", metadata);
												String args = StringArgumentType.getString(context, "args");
												root.add("items", ExtractItems.extractItems(context.getSource().getEntityOrException()));
												root.add("blocks", ExtractBlocks.extractBlocks(context.getSource().getEntityOrException()));
												root.add("fluids", ExtractFluids.extractFluids());
												root.add("entities", ExtractEntities.extractEntities(context.getSource().getEntityOrException()));
												try {
													String path = JSONWriter.write(args, root);
													context.getSource().sendSuccess(
														Component.literal("Wrote to: " + path),
														true
													);
												} catch(IOException e) {
													context.getSource().sendFailure(
														Component.literal("Failed to write to \"" + args + "\"")
													);
													return 0;
												}
												return 1;
											})
							)
							.executes(context -> {
									context.getSource().sendFailure(
											Component.literal("Usage: /extract <file path>")
									);
									return 0;
							})
			);
	}
}
