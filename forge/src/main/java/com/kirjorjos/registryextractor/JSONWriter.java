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
		
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

		if (path.startsWith("~")) {
			path = System.getProperty("user.home") + path.substring(1);
		}
		Path resolved = Paths.get(path).normalize().toAbsolutePath();
		if (Files.isDirectory(resolved)) {
			resolved = resolved.resolve("registry.json");
		}

		try (FileWriter writer = new FileWriter(resolved.toString())) {
				gson.toJson(json, writer);
		} catch (IOException e) {
			RegistryExtractor.getLogger().info(path);
			RegistryExtractor.getLogger().warn(e.getLocalizedMessage());
			throw e;
		}

		return resolved.toString();
	}

}
