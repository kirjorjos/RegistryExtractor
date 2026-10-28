package com.kirjorjos.registryextractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.material.Fluid;

import java.lang.reflect.Field;

public class ExtractItems {
	public static JsonObject extractItems(Entity player) {
			JsonObject items = new JsonObject();

			for (Item item : Registry.ITEM) {
					@SuppressWarnings("null")
					ItemStack stack = new ItemStack(item);
					ResourceLocation id = Registry.ITEM.getKey(item);

					String namespace = id.getNamespace();
					String path = id.getPath();

					// create namespace object if needed
					items.add(namespace, items.has(namespace) ? items.get(namespace).getAsJsonObject() : new JsonObject());
					JsonObject nsObj = items.get(namespace).getAsJsonObject();

					JsonObject itemJson = new JsonObject();
					itemJson.addProperty("name", stack.getHoverName().getString());

					String modName = FabricLoader.getInstance().getModContainer(namespace)
							.map(c -> c.getMetadata().getName())
							.orElse(namespace);
					itemJson.addProperty("mod", modName);

					itemJson.addProperty("block", item instanceof BlockItem bi ? Registry.BLOCK.getKey(bi.getBlock()).toString() : null);
					itemJson.addProperty("maxSize", item.getMaxStackSize());
					itemJson.addProperty("maxDamage", stack.isDamageableItem() ? item.getMaxDamage() : -1);
					itemJson.addProperty("isEnchantable", !stack.isEmpty() && stack.isEnchantable());
					// Fabric has no fuel-value API: burn time is resolved per recipe at runtime, so there is
					// nothing to read for an arbitrary stack.
					itemJson.addProperty("fuelBurnTime", 0);

					// Forge capabilities have no Fabric equivalent, so nothing can answer for energy.
					itemJson.addProperty("feContainer", false);
					itemJson.addProperty("feCapacity", 0);

					if (item instanceof BucketItem bucket) {
						itemJson.addProperty("isFluidContainer", true);
						Fluid content = bucketFluid(bucket);
						itemJson.addProperty("fluid", content == null ? null : Registry.FLUID.getKey(content).toString());
						itemJson.addProperty("fluidCapacity", 1000);
					} else {
						itemJson.addProperty("isFluidContainer", false);
						itemJson.addProperty("fluidCapacity", 0);
					}

					// Substitute for Forge's item-handler capability: ask the item's block, if any, for its
					// own container. This covers vanilla-style inventories without a capability layer.
					int invSize = 0;
					if (item instanceof BlockItem bi && bi.getBlock() instanceof EntityBlock entityBlock) {
						BlockEntity blockEntity = entityBlock.newBlockEntity(
								player.blockPosition(), bi.getBlock().defaultBlockState());
						if (blockEntity instanceof Container container) {
							invSize = container.getContainerSize();
						}
					}
					itemJson.addProperty("inventorySize", invSize);

					int toolTier = 0;
					float efficiency = 1.0f;
					if (item instanceof TieredItem tiered) {
						toolTier = tiered.getTier().getLevel();
					}
					if (item instanceof DiggerItem digger) {
						efficiency = digger.getTier().getSpeed();
					}
					itemJson.addProperty("toolTier", toolTier);
					itemJson.addProperty("efficiency", efficiency);

					// Forge's IPlantable has no Fabric counterpart, so plantability cannot be answered.
					itemJson.addProperty("isPlantable", false);
					itemJson.addProperty("plantType", "none");
					itemJson.addProperty("plant", "");

					// tooltips
					JsonArray tooltipArray = new JsonArray();
					for (var component : stack.getTooltipLines(null, TooltipFlag.Default.NORMAL)) {
							tooltipArray.add(component.getString());
					}
					itemJson.add("tooltip", tooltipArray);

					JsonArray tagsArray = new JsonArray();
					item.builtInRegistryHolder().tags()
					.forEach(tag -> {
							String tagName = tag.location().toString();
							tagsArray.add(tagName);
					});
					itemJson.add("tags", tagsArray);

					nsObj.add(path, itemJson);
			}

			return items;
	}

	/**
	 * Vanilla's {@code BucketItem} keeps its fluid in a private field; Forge is what exposes
	 * {@code getFluid()}. Read the field rather than invent a mapping, and fall back to the schema's
	 * neutral null if it ever moves.
	 */
	private static final Field BUCKET_CONTENT = bucketContentField();

	private static Field bucketContentField() {
		try {
			Field field = BucketItem.class.getDeclaredField("content");
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			RegistryExtractor.getLogger().warn("BucketItem has no fluid field; bucket fluids will be null", e);
			return null;
		}
	}

	private static Fluid bucketFluid(BucketItem bucket) {
		if (BUCKET_CONTENT == null) return null;
		try {
			return (Fluid) BUCKET_CONTENT.get(bucket);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

}
