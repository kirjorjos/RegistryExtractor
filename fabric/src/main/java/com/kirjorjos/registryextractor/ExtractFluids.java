package com.kirjorjos.registryextractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

public class ExtractFluids {
	@SuppressWarnings("null")
	public static JsonObject extractFluids() {
    JsonObject fluids = new JsonObject();

    for (Fluid fluid : Registry.FLUID) {
			ResourceLocation id = Registry.FLUID.getKey(fluid);
			if (id == null) continue;

			// Skip flowing fluids
			if (fluid instanceof FlowingFluid flowing &&
					!flowing.isSource(flowing.defaultFluidState())) {
					continue;
			}

			String namespace = id.getNamespace();
			String path = id.getPath();

			JsonObject namespaceObj = fluids.has(namespace)
							? fluids.getAsJsonObject(namespace)
							: new JsonObject();

			JsonObject fluidJson = new JsonObject();

			Block block = fluid.defaultFluidState().createLegacyBlock().getBlock();

			// Name. Forge reads this from its FluidAttributes; vanilla 1.18.2 has no such object, so
			// the placed block's name is the closest available substitute.
			String name = block == Blocks.AIR ? path : block.getName().getString();
			fluidJson.addProperty("name", name);
			// Forge answers this from FluidAttributes; vanilla has no such object, but the registry id
			// is what the key is made from, so the same "fluid.<ns>.<path>" comes out of both loaders.
			fluidJson.addProperty("translationKey", Util.makeDescriptionId("fluid", id));

			// Mod
			String modName = FabricLoader.getInstance().getModContainer(namespace)
					.map(c -> c.getMetadata().getName())
					.orElse(namespace);
			fluidJson.addProperty("mod", modName);

			// Block
			fluidJson.addProperty("block",
							block == Blocks.AIR ? null :
													Registry.BLOCK.getKey(block).toString());
			fluidJson.addProperty("lightLevel", block.defaultBlockState().getLightEmission());

			// Physical properties. These live on Forge's FluidAttributes, which does not exist in
			// vanilla 1.18.2, so there is nothing to read and the schema keeps its neutral values.
			fluidJson.addProperty("density", 0);
			fluidJson.addProperty("temperature", 0);
			fluidJson.addProperty("viscosity", 0);
			fluidJson.addProperty("lighterThanAir", false);
			fluidJson.addProperty("rarity", "COMMON");

			// Sounds. Same story: fluid sound events are Forge metadata in this version.
			JsonObject sounds = new JsonObject();
			sounds.addProperty("bucketEmpty", (String) null);
			sounds.addProperty("bucketFill", (String) null);
			sounds.addProperty("vaporize", (String) null);
			fluidJson.add("sounds", sounds);

			// Bucket
			Item bucket = fluid.getBucket();
			fluidJson.addProperty("bucket",
							bucket == Items.AIR ? null :
													Registry.ITEM.getKey(bucket).toString());

			// Tags
			JsonArray tags = new JsonArray();
			fluid.builtInRegistryHolder().tags()
				.forEach(tag -> {
						String tagName = tag.location().toString();
						tags.add(tagName);
				});
			fluidJson.add("tags", tags);

			namespaceObj.add(path, fluidJson);
			fluids.add(namespace, namespaceObj);
    }

    return fluids;
	}

}
