package com.kirjorjos.registryextractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rewrites one extraction into the per-locale artifact the docs site consumes.
 *
 * <p>The extraction itself stays exactly what the client rendered; this only re-resolves the
 * {@code name} of each entry from the loaded mods' packaged {@code assets/<namespace>/lang/<code>.json}
 * files. A key that a mod does not ship in that language is reported as missing ({@code translated:
 * false} and a null {@code name}) rather than as the key text or as the client's English name: the
 * docs site treats "upstream ships no translation" as "show nothing", and must never invent one.
 *
 * <p>The languages are read from the resource manager rather than by restarting the client, because
 * every entry already carries the {@code translationKey} the name came from.
 */
public final class LocaleExtraction {
	/**
	 * The file a locale run leaves in the client's working directory (the run dir): the docs locale
	 * codes to emit, separated by commas or whitespace. Absent means today's single extraction.
	 */
	public static final String REQUEST_FILE = "extractor-locales.txt";

	private static final String[] ROOTS = {"items", "blocks", "fluids", "entities"};

	private LocaleExtraction() {}

	/** The requested docs locale codes, in the order the request file lists them. */
	public static List<String> requested() {
		Path file = Paths.get(REQUEST_FILE);
		if (!Files.isRegularFile(file)) {
			return List.of();
		}
		String text;
		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			RegistryExtractor.getLogger().warn("could not read {}: {}",
					file.toAbsolutePath(), e.toString());
			return List.of();
		}
		Set<String> codes = new LinkedHashSet<>();
		for (String code : text.split("[,\\s]+")) {
			if (!code.isBlank()) {
				codes.add(code.trim());
			}
		}
		return new ArrayList<>(codes);
	}

	/**
	 * One extraction rewritten for {@code locale}, a docs code such as {@code fr_FR}.
	 *
	 * <p>The rewrites are additive and per entry: {@code modName} is written where the docs reads it,
	 * {@code translated} says whether the entry's {@code translationKey} exists in this locale's lang
	 * files, and {@code name} is null when it does not. Entity {@code displayName} mirrors {@code name}.
	 */
	public static JsonObject forLocale(JsonObject extraction, String locale) {
		JsonObject root = extraction.deepCopy();
		JsonObject metadata = root.getAsJsonObject("metadata");
		metadata.addProperty("schemaVersion", "2");
		metadata.addProperty("locale", locale);
		metadata.addProperty("minecraftLocale", minecraftCode(locale));
		metadata.add("mods", loadedMods());

		Map<String, String> strings = langStrings(minecraftCode(locale), namespaces(root));
		// Tooltips are rendered strings, not keys: only the locale the client itself runs in can have
		// one, and every harness run is en_US. Any other locale reports null rather than English text.
		boolean tooltips = "en_us".equals(minecraftCode(locale));
		int total = 0;
		int translated = 0;

		for (String registry : ROOTS) {
			JsonObject byNamespace = root.getAsJsonObject(registry);
			if (byNamespace == null) {
				continue;
			}
			for (Map.Entry<String, JsonElement> namespace : byNamespace.entrySet()) {
				for (Map.Entry<String, JsonElement> path : namespace.getValue().getAsJsonObject().entrySet()) {
					JsonObject entry = path.getValue().getAsJsonObject();

					String modName = string(entry, "mod");
					entry.addProperty("modName", modName == null ? namespace.getKey() : modName);

					String key = string(entry, "translationKey");
					String name = key == null ? null : strings.get(key);
					if (name != null && name.isEmpty()) {
						name = null;
					}
					if (name == null) {
						entry.add("name", null);
						entry.addProperty("translated", false);
					} else {
						entry.addProperty("name", name);
						entry.addProperty("translated", true);
						translated++;
					}
					total++;

					// Entities carry the same rendered name twice; both are the site's business.
					if (entry.has("displayName")) {
						if (name == null) {
							entry.add("displayName", null);
						} else {
							entry.addProperty("displayName", name);
						}
					}
					if (entry.has("tooltip") && !tooltips) {
						entry.add("tooltip", null);
					}
				}
			}
		}

		RegistryExtractor.getLogger().info("locale {} ({}) resolved {}/{} names",
				locale, minecraftCode(locale), translated, total);
		if (total > 0 && translated == 0) {
			RegistryExtractor.getLogger().warn(
					"locale {} matched no loaded lang file: is it a docs code, e.g. fr_FR?", locale);
		}
		return root;
	}

	/** The docs code ({@code fr_FR}) the language file code it names ({@code fr_fr}). */
	public static String minecraftCode(String locale) {
		return locale.toLowerCase(Locale.ROOT);
	}

	private static String string(JsonObject object, String member) {
		JsonElement value = object.get(member);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return null;
		}
		return value.getAsString();
	}

	private static Set<String> namespaces(JsonObject root) {
		Set<String> namespaces = new TreeSet<>();
		for (String registry : ROOTS) {
			JsonObject byNamespace = root.getAsJsonObject(registry);
			if (byNamespace != null) {
				namespaces.addAll(byNamespace.keySet());
			}
		}
		namespaces.add("minecraft");
		return namespaces;
	}

	/**
	 * Every translation the loaded packs ship for one language file code, keyed by translation key.
	 * A namespace with no such file is normal and contributes nothing.
	 */
	private static Map<String, String> langStrings(String code, Set<String> namespaces) {
		Map<String, String> strings = new HashMap<>();
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		for (String namespace : namespaces) {
			ResourceLocation location = new ResourceLocation(namespace, "lang/" + code + ".json");
			var found = resources.getResource(location);
			if (found.isEmpty()) {
				RegistryExtractor.getLogger().debug("no {}", location);
				continue;
			}
			InputStream stream;
			try {
				stream = found.get().open();
			} catch (IOException | RuntimeException e) {
				RegistryExtractor.getLogger().debug("no {}: {}", location, e.toString());
				continue;
			}
			try (InputStream in = stream;
					InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (!parsed.isJsonObject()) {
					continue;
				}
				for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
					JsonElement value = entry.getValue();
					if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
						strings.put(entry.getKey(), value.getAsString());
					}
				}
			} catch (IOException | RuntimeException e) {
				RegistryExtractor.getLogger().warn("could not read {}: {}", location, e.toString());
			}
		}
		return strings;
	}

	/** The loaded mod set, sorted by id, so a copied artifact says which builds produced it. */
	private static JsonArray loadedMods() {
		var mods = new ArrayList<>(ModList.get().getMods());
		mods.sort((left, right) -> left.getModId().compareTo(right.getModId()));
		JsonArray array = new JsonArray();
		for (var mod : mods) {
			JsonObject entry = new JsonObject();
			entry.addProperty("id", mod.getModId());
			entry.addProperty("name", mod.getDisplayName());
			entry.addProperty("version", mod.getVersion().toString());
			array.add(entry);
		}
		return array;
	}
}
