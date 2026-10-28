package com.kirjorjos.registryextractor;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class ExtractFluids {
	@SuppressWarnings("null")
	public static JsonObject extractFluids() {
    JsonObject fluids = new JsonObject();

    for (Fluid fluid : ForgeRegistries.FLUIDS) {
			ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
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

			FluidAttributes type = fluid.getAttributes();
			JsonObject fluidJson = new JsonObject();

			// Name
			fluidJson.addProperty("name",
					type.getDisplayName(new FluidStack(fluid, FluidAttributes.BUCKET_VOLUME)).getString());

			// Mod
			ModContainer mod = ModList.get().getModContainerById(namespace).orElse(null);
			fluidJson.addProperty("mod",
							mod != null ? mod.getModInfo().getDisplayName() : namespace);

			// Block
			Block block = fluid.defaultFluidState().createLegacyBlock().getBlock();
			fluidJson.addProperty("block",
							block == Blocks.AIR ? null :
											ForgeRegistries.BLOCKS.getKey(block).toString());
			fluidJson.addProperty("lightLevel", block.defaultBlockState().getLightEmission());

			// Physical properties
			fluidJson.addProperty("density", type.getDensity());
			fluidJson.addProperty("temperature", type.getTemperature());
			fluidJson.addProperty("viscosity", type.getViscosity());
			fluidJson.addProperty("lighterThanAir", type.isLighterThanAir());
			fluidJson.addProperty("rarity", type.getRarity().name());

			// Sounds
			JsonObject sounds = new JsonObject();
			sounds.addProperty("bucketEmpty", soundId(type.getEmptySound()));
			sounds.addProperty("bucketFill", soundId(type.getFillSound()));
			// 1.18.2's FluidAttributes carries no vaporize sound; the key stays so the schema is identical.
			sounds.add("vaporize", JsonNull.INSTANCE);
			fluidJson.add("sounds", sounds);

			// Bucket
			Item bucket = fluid.getBucket();
			fluidJson.addProperty("bucket",
							bucket == Items.AIR ? null :
											ForgeRegistries.ITEMS.getKey(bucket).toString());

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

	private static String soundId(@Nullable SoundEvent sound) {
		return sound == null ? null : sound.getLocation().toString();
	}

}
