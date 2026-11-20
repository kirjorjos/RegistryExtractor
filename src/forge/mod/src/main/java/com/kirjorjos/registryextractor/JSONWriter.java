package com.kirjorjos.registryextractor;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class JSONWriter {

	public static String write(String path, JsonObject json) throws IOException {
		return write(resolve(path), json);
	}

	/**
	 * The absolute file a command argument names: a leading {@code ~} expands to the user's home
	 * directory and a path that resolves to a directory gets {@code registry.json} appended.
	 */
	public static Path resolve(String path) {
		if (path.startsWith("~")) {
			path = System.getProperty("user.home") + path.substring(1);
		}
		Path resolved = Paths.get(path).normalize().toAbsolutePath();
		if (Files.isDirectory(resolved)) {
			resolved = resolved.resolve("registry.json");
		}
		return resolved;
	}

	/**
	 * The file one locale's copy belongs in: {@code registry.json} becomes
	 * {@code registry-fr_FR.json} and a path without an extension gains {@code -fr_FR}.
	 */
	public static Path localePath(Path resolved, String locale) {
		String name = resolved.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String localized = dot > 0
				? name.substring(0, dot) + "-" + locale + name.substring(dot)
				: name + "-" + locale;
		return resolved.resolveSibling(localized);
	}

	public static String write(Path resolved, JsonObject json) throws IOException {

		Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

		try (FileWriter writer = new FileWriter(resolved.toString())) {
			gson.toJson(json, writer);
		} catch (IOException e) {
			RegistryExtractor.getLogger().info(resolved.toString());
			RegistryExtractor.getLogger().warn(e.getLocalizedMessage());
			throw e;
		}

		return resolved.toString();
	}

}
