package com.kirjorjos.registryextractor;

import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

public class ExtractCommand {

	private static final String DESCRIPTION =
			"Generated game registries for WIP Integrated Dynamics project";

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

	/** One walk of the loaded registries, in the client's current language. */
	private static JsonObject extract(Entity player) {
		JsonObject root = new JsonObject();

		// Create the "metadata" object
		JsonObject metadata = new JsonObject();
		metadata.addProperty("schemaVersion", "1");
		metadata.addProperty("gameVersion", gameVersion());
		metadata.addProperty("description", DESCRIPTION);

		// Add the metadata object to the root
		root.add("metadata", metadata);
		root.add("items", ExtractItems.extractItems(player));
		root.add("blocks", ExtractBlocks.extractBlocks(player));
		root.add("fluids", ExtractFluids.extractFluids());
		root.add("entities", ExtractEntities.extractEntities(player));
		return root;
	}

	/**
	 * Writes the extraction: one file when no locale was requested (today's shape, which the suite's
	 * golden pins), or one suffixed file per requested locale, resolved from the loaded lang files.
	 */
	private static int write(CommandSourceStack source, String args, JsonObject root) {
		List<String> locales = LocaleExtraction.requested();
		if (locales.isEmpty()) {
			try {
				String path = JSONWriter.write(args, root);
				source.sendSuccess(Component.literal("Wrote to: " + path), true);
				return 1;
			} catch (IOException e) {
				source.sendFailure(Component.literal("Failed to write to \"" + args + "\""));
				return 0;
			}
		}

		Path resolved = JSONWriter.resolve(args);
		int written = 0;
		for (String locale : locales) {
			Path target = JSONWriter.localePath(resolved, locale);
			try {
				JSONWriter.write(target, LocaleExtraction.forLocale(root, locale));
				source.sendSuccess(Component.literal("Wrote " + locale + " to: " + target), true);
				written++;
			} catch (IOException e) {
				source.sendFailure(Component.literal(
						"Failed to write " + locale + " to \"" + args + "\""));
			}
		}
		return written == locales.size() ? 1 : 0;
	}

	@SuppressWarnings("null")
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("extract")
						.then(
								Commands.argument("args", StringArgumentType.greedyString())
										.executes(context -> write(
												context.getSource(),
												StringArgumentType.getString(context, "args"),
												extract(context.getSource().getEntityOrException())))
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
