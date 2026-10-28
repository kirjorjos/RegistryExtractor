package com.kirjorjos.registryextractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.minecraft.network.chat.TextComponent;

public class ExtractCommand {

	/**
	 * The minecraft version this build declares in its own {@code fabric.mod.json} dependency.
	 *
	 * <p>Read from the mod's own metadata rather than declared a second time, so there is one source of
	 * truth and a version branch cannot export a plausible-looking lie. There is deliberately no fallback:
	 * if the dependency is missing or unreadable that is a bug in the metadata, not a state to render.
	 */
	private static String gameVersion() {
		try (InputStream in = ExtractCommand.class.getResourceAsStream("/fabric.mod.json")) {
			if (in == null) {
				throw new IllegalStateException(
						"fabric.mod.json is missing from the jar, so gameVersion cannot be derived");
			}
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonElement minecraft = root.has("depends")
					? root.getAsJsonObject("depends").get("minecraft")
					: null;
			if (minecraft == null) {
				throw new IllegalStateException(
						"fabric.mod.json declares no minecraft dependency, so gameVersion cannot be derived");
			}
			String declared = minecraft.isJsonArray()
					? minecraft.getAsJsonArray().get(0).getAsString()
					: minecraft.getAsString();
			// Normalise a range like ">=1.18.2" to the plain version the exporter has always written.
			return declared.replaceFirst("^[\\s<>=~^]+", "").trim();
		} catch (IOException e) {
			throw new IllegalStateException(
					"fabric.mod.json could not be read, so gameVersion cannot be derived", e);
		}
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
														new TextComponent("Wrote to: " + path),
														true
													);
												} catch(IOException e) {
													context.getSource().sendFailure(
														new TextComponent("Failed to write to \"" + args + "\"")
													);
													return 0;
												}
												return 1;
											})
							)
							.executes(context -> {
									context.getSource().sendFailure(
											new TextComponent("Usage: /extract <file path>")
									);
									return 0;
							})
			);
	}
}
