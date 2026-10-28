package com.kirjorjos.registryextractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

public class ExtractBlocks {

	@SuppressWarnings({ "null", "unchecked", "rawtypes" })
	public static JsonObject extractBlocks(Entity player) {
    JsonObject blocks = new JsonObject();

    for (Block block : Registry.BLOCK) {
			ResourceLocation id = Registry.BLOCK.getKey(block);
			if (id == null) continue;

			String namespace = id.getNamespace();
			String path = id.getPath();

			JsonObject namespaceObj = blocks.has(namespace)
							? blocks.getAsJsonObject(namespace)
							: new JsonObject();

			JsonObject blockJson = new JsonObject();

			// name
			blockJson.addProperty("name", block.getName().getString());

			// mod
			String modName = FabricLoader.getInstance().getModContainer(namespace)
					.map(c -> c.getMetadata().getName())
					.orElse(namespace);
			blockJson.addProperty("mod", modName);

			// item
			Item item = block.asItem();
			blockJson.addProperty("item", Registry.ITEM.getKey(item).toString());

			// opaque
			blockJson.addProperty("opaque",
							block.defaultBlockState().isSolidRender(player.level, BlockPos.ZERO));

			// shearable: Forge's IForgeShearable has no Fabric counterpart.
			blockJson.addProperty("shearable", false);

			// sounds
			SoundType sound = block.defaultBlockState().getSoundType();
			JsonObject sounds = new JsonObject();
			sounds.addProperty("break", sound.getBreakSound().getLocation().toString());
			sounds.addProperty("place", sound.getPlaceSound().getLocation().toString());
			sounds.addProperty("step", sound.getStepSound().getLocation().toString());
			blockJson.add("sounds", sounds);

			// plantable: Forge's IPlantable has no Fabric counterpart.
			blockJson.addProperty("isPlantable", false);
			blockJson.addProperty("plantType", "none");
			blockJson.addProperty("plant", "");

			// plant age
			int maxPlantAge = -1;
			if (block instanceof CropBlock crop) {
					maxPlantAge = crop.getMaxAge();
			}
			blockJson.addProperty("plantAge", 0);
			blockJson.addProperty("age", 0);
			blockJson.addProperty("maxPlantAge", maxPlantAge);

			// possible properties
			JsonObject possibleProperties = new JsonObject();
			for (Property<?> property : block.defaultBlockState().getProperties()) {
				JsonArray values = new JsonArray();
				for (Object value : property.getPossibleValues()) {
					values.add(((Property)property).getName((Comparable)value));
				}
				possibleProperties.add(property.getName(), values);
			}
			blockJson.add("possibleProperties", possibleProperties);

			// fluid
			FluidState fluidState = block.defaultBlockState().getFluidState();

			String fluidId = fluidState.isEmpty()
							? null
							: Registry.FLUID.getKey(fluidState.getType()).toString();

			blockJson.addProperty("fluid", fluidId);
			blockJson.addProperty("fluidCapacity", 0);

			// energy
			JsonObject energy = new JsonObject();
			energy.addProperty("isContainer", block instanceof EntityBlock);
			energy.addProperty("capacity", 0);
			energy.addProperty("stored", 0);
			blockJson.add("energy", energy);

			// destroy speed
			blockJson.addProperty("destroySpeed", block.defaultBlockState().getDestroySpeed(player.level, BlockPos.ZERO));

			// inventory
			blockJson.add("inventory", new JsonArray());

			// tags
			JsonArray tags = new JsonArray();
			int requiredTier = 0;
			var holder = block.builtInRegistryHolder();

			for (var tag : holder.tags().toList()) {
				String tagName = tag.location().toString();
				tags.add(tagName);
				if (tagName.equals("minecraft:needs_stone_tool")) requiredTier = Math.max(requiredTier, 1);
				if (tagName.equals("minecraft:needs_iron_tool")) requiredTier = Math.max(requiredTier, 2);
				if (tagName.equals("minecraft:needs_diamond_tool")) requiredTier = Math.max(requiredTier, 3);
				if (tagName.equals("minecraft:needs_netherite_tool")) requiredTier = Math.max(requiredTier, 4);
			}
			blockJson.addProperty("requiredTier", requiredTier);
			blockJson.add("tags", tags);

			namespaceObj.add(path, blockJson);
			blocks.add(namespace, namespaceObj);
    }

    return blocks;
	}

}
