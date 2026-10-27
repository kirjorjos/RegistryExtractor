package com.kirjorjos.registryextractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.IForgeShearable;

import java.lang.reflect.Method;

public class ExtractEntities {
	public static JsonObject extractEntities(Entity player) {
    JsonObject entities = new JsonObject();

    for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
			ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
			if (id == null) continue;

			String namespace = id.getNamespace();
			String path = id.getPath();

			JsonObject namespaceObj = entities.has(namespace)
							? entities.getAsJsonObject(namespace)
							: new JsonObject();

			JsonObject entityJson = new JsonObject();

			// Basic info
			entityJson.addProperty("name", type.getDescription().getString());
			entityJson.addProperty("displayName", type.getDescription().getString());
			String modName = ModList.get().getModContainerById(namespace)
					.map(c -> c.getModInfo().getDisplayName())
					.orElse(namespace);
			entityJson.addProperty("mod", modName);
			entityJson.addProperty("modName", modName);

			// Class traits
			MobCategory category = type.getCategory();
			// Integrated Dynamics: IsMob is typically Monsters only.
			entityJson.addProperty("mob", category.equals(MobCategory.MONSTER));
			entityJson.addProperty("animal", category.equals(MobCategory.CREATURE) || category.equals(MobCategory.WATER_CREATURE));
			entityJson.addProperty("player", type.equals(EntityType.PLAYER));
			entityJson.addProperty("minecart", id.toString().contains("minecart"));
			entityJson.addProperty("isItem", type.equals(EntityType.ITEM));

			// Size
			entityJson.addProperty("width", type.getWidth());
			entityJson.addProperty("height", type.getHeight());

			// Instance-level defaults
			double health = 0;
			boolean shearable = false;
			JsonArray breedableList = new JsonArray();
			String hurtSound = "";
			String deathSound = "";

			try {
				Entity entity = type.create(player.level);
				if (entity != null) {
					if (entity instanceof LivingEntity living) {
						health = living.getMaxHealth();
						
						try {
							Class<?> clazz = living.getClass();
							Method getHurtSound = null;
							Method getDeathSound = null;
							
							while (clazz != null && clazz != Object.class) {
								for (Method m : clazz.getDeclaredMethods()) {
									String name = m.getName();
									// getHurtSound(DamageSource) -> m_7975_
									if ((name.equals("getHurtSound") || name.equals("m_7975_")) && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(DamageSource.class)) {
										getHurtSound = m;
									}
									// getDeathSound() -> m_7515_ or m_5592_
									if ((name.equals("getDeathSound") || name.equals("m_7515_") || name.equals("m_5592_")) && m.getParameterCount() == 0) {
										getDeathSound = m;
									}
									if (getHurtSound != null && getDeathSound != null) break;
								}
								if (getHurtSound != null && getDeathSound != null) break;
								clazz = clazz.getSuperclass();
							}

							if (getHurtSound != null) {
								getHurtSound.setAccessible(true);
								SoundEvent hurtSoundEvent = (SoundEvent) getHurtSound.invoke(living, DamageSource.GENERIC);
								if (hurtSoundEvent != null) {
									hurtSound = hurtSoundEvent.getLocation().toString();
								}
							}

							if (getDeathSound != null) {
								getDeathSound.setAccessible(true);
								SoundEvent deathSoundEvent = (SoundEvent) getDeathSound.invoke(living);
								if (deathSoundEvent != null) {
									deathSound = deathSoundEvent.getLocation().toString();
								}
							}
							
							// Fallback if deathSound is still suspicious or empty
							if (deathSound.isEmpty() || deathSound.contains("ambient")) {
								for (Method m : living.getClass().getMethods()) {
									if (m.getReturnType().equals(SoundEvent.class) && m.getParameterCount() == 0) {
										String name = m.getName().toLowerCase();
										if (name.contains("death") || name.equals("m_5592_")) {
											try {
												m.setAccessible(true);
												SoundEvent se = (SoundEvent) m.invoke(living);
												if (se != null && !se.getLocation().toString().contains("ambient")) {
													deathSound = se.getLocation().toString();
													break;
												}
											} catch (Exception ignored) {}
										}
									}
								}
							}
						} catch (Exception e) {
							// Ignore sound extraction errors
						}
					}
					if (entity instanceof IForgeShearable) {
						shearable = true;
					}
					if (entity instanceof Animal animal) {
						for (Item item : ForgeRegistries.ITEMS) {
							if (animal.isFood(new ItemStack(item))) {
								breedableList.add(ForgeRegistries.ITEMS.getKey(item).toString());
							}
						}
					}
					// Discard the entity immediately
					entity.discard();
				}
			} catch (Exception e) {
				// Fallback to simple logic or defaults
			}

			entityJson.addProperty("health", health);
			entityJson.addProperty("burning", false);
			entityJson.addProperty("wet", false);
			entityJson.addProperty("crouching", false);
			entityJson.addProperty("eating", false);
			entityJson.addProperty("age", 0);
			entityJson.addProperty("breedingAge", 0);
			entityJson.addProperty("child", false);
			entityJson.addProperty("inLove", false);
			entityJson.addProperty("shearable", shearable);
			entityJson.addProperty("isSheared", false);
			entityJson.add("breedableList", breedableList);
			
			// If NBT is empty, don't even add it or add null so Properties.wrapValue handles it as iNull
			entityJson.add("NBT", null);

			entityJson.add("inventory", new JsonArray());
			entityJson.add("armorInventory", new JsonArray());
			entityJson.add("fluids", new JsonArray());
			entityJson.addProperty("entityType", id.toString());
			entityJson.addProperty("id", id.toString());
			
			JsonArray tagsArray = new JsonArray();
			type.builtInRegistryHolder().tags()
			.forEach(tag -> {
					String tagName = tag.location().toString();
					tagsArray.add(tagName);
			});
			entityJson.add("tags", tagsArray);

			JsonObject sounds = new JsonObject();
			sounds.addProperty("hurt", hurtSound);
			sounds.addProperty("death", deathSound);
			entityJson.add("sounds", sounds);
			entityJson.addProperty("hurtSound", hurtSound);
			entityJson.addProperty("deathSound", deathSound);

			// Add to namespace
			namespaceObj.add(path, entityJson);
			entities.add(namespace, namespaceObj);
    }

    return entities;
	}

}
