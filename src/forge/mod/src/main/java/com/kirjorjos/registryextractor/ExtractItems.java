package com.kirjorjos.registryextractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

public class ExtractItems {
	public static JsonObject extractItems(Entity player) {
			JsonObject items = new JsonObject();

			for (Item item : ForgeRegistries.ITEMS) {
					@SuppressWarnings("null")
					ItemStack stack = new ItemStack(item);
					ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

					String namespace = id.getNamespace();
					String path = id.getPath();

					// create namespace object if needed
					items.add(namespace, items.has(namespace) ? items.get(namespace).getAsJsonObject() : new JsonObject());
					JsonObject nsObj = items.get(namespace).getAsJsonObject();

					JsonObject itemJson = new JsonObject();
					itemJson.addProperty("name", stack.getHoverName().getString());
					
					String modName = ModList.get().getModContainerById(namespace)
							.map(c -> c.getModInfo().getDisplayName())
							.orElse(namespace);
					itemJson.addProperty("mod", modName);
					
					itemJson.addProperty("block", item instanceof BlockItem bi ? ForgeRegistries.BLOCKS.getKey(bi.getBlock()).toString() : null);
					itemJson.addProperty("maxSize", item.getMaxStackSize());
					itemJson.addProperty("maxDamage", item.isDamageable(stack) ? item.getMaxDamage() : -1);
					itemJson.addProperty("isEnchantable", !stack.isEmpty() && stack.isEnchantable());
					itemJson.addProperty("fuelBurnTime", ForgeHooks.getBurnTime(stack, null));
					
					var energyCap = stack.getCapability(CapabilityEnergy.ENERGY);
					itemJson.addProperty("feContainer", energyCap.isPresent());
					itemJson.addProperty("feCapacity", energyCap.map(e -> e.getMaxEnergyStored()).orElse(0));
					
					if (item instanceof BucketItem bucket) {
						itemJson.addProperty("isFluidContainer", true);
						itemJson.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(bucket.getFluid()).toString());
						itemJson.addProperty("fluidCapacity", 1000);
					} else {
						itemJson.addProperty("isFluidContainer", false);
						itemJson.addProperty("fluidCapacity", 0);
					}

					int invSize = 0;
					var handlerCap = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
					if (handlerCap.isPresent()) {
						invSize = handlerCap.orElse(null).getSlots();
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

					// plantable
					boolean isPlantable = item instanceof IPlantable;
					if (!isPlantable && item instanceof BlockItem bi) {
						isPlantable = bi.getBlock() instanceof IPlantable;
					}
					itemJson.addProperty("isPlantable", isPlantable);
					String plantType = "none";
					String plant = "";
					if (isPlantable) {
						IPlantable plantable = (item instanceof IPlantable) ? (IPlantable) item : (IPlantable) ((BlockItem) item).getBlock();
						plantType = plantable.getPlantType(player.level, player.blockPosition()).getName();
						plant = ForgeRegistries.BLOCKS.getKey(plantable.getPlant(player.level, player.blockPosition()).getBlock()).toString();
					}
					itemJson.addProperty("plantType", plantType);
					itemJson.addProperty("plant", plant);

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

}
