# Mod loaders that exist for Minecraft 1.18.2

Research behind the 1.18.2 loader targets on `1.18.2/development`, checked against registries and loader
metadata on **2026-10-28**.

## Answer

**Three client-capable mod loaders shipped for 1.18.2: Forge, Fabric and Quilt.** No other loader exists
for this version, so adding all of them means two new build targets (Fabric and Quilt), and Quilt runs the
Fabric jar without a code change.

## Supported for 1.18.2

| Loader | Status here | Pinned version | Evidence |
| --- | --- | --- | --- |
| Minecraft Forge | shipping | `1.18.2-40.3.12` | `forge/build.gradle` (unchanged from the Forge port) |
| Fabric | added | Loader `0.20.1`, Fabric API `0.77.0+1.18.2` | `meta.fabricmc.net/v2/versions/loader/1.18.2` (loader `0.20.1`, `stable: true`); `maven.fabricmc.net` lists `0.77.0+1.18.2` as the newest Fabric API for this version |
| Quilt | loads the Fabric jar | Loader `0.20.0-beta.9`, QFAPI `1.0.0-beta.28` (`+ QSL 1.1.0-beta.26`) | `meta.quiltmc.org/v3/versions/loader/1.18.2` (newest build); Modrinth `qsl` project, 1.18.2 filter |

### The Quilt side is a dependency-range decision, not a second codebase

Quilt Loader loads `fabric.mod.json` mods natively, so there is no `quilt.mod.json` here. The catch is the
API version: QFAPI `1.0.0-beta.28` declares `provides: [{ "id": "fabric-api", "version": "0.67.0" }]` (read
from the jar's own `quilt.mod.json`), while the newest Fabric API for 1.18.2 is `0.77.0`. Declaring
`fabric-api >=0.77.0` would therefore make Quilt refuse the mod.

The mod only uses Fabric API's command-registration callback, so the declared floor is **`fabric-api
>=0.67.0`** — the version QFAPI actually supplies. That floor was verified by compiling the module against
`0.67.0+1.18.2`; the build itself still compiles against `0.77.0+1.18.2`.

## Ruled out, and why

| Platform | Why not 1.18.2 |
| --- | --- |
| NeoForge | **No 1.18.2 artifact.** `maven.neoforged.net`: `net.neoforged:forge` metadata contains zero `1.18.2` versions (earliest is `1.20.1-47.1.7`); `net.neoforged:neoforge` starts at `20.2.x-beta`. |
| Legacy Fabric | Fabric Loader for the 1.0–1.13.2 era; stops below 1.18.2. |
| Ornithe | Fabric/Quilt for pre-classic … 1.13-era versions; stops below 1.18.2. |
| Babric / StationAPI | Beta 1.7.3 only. |
| LiteLoader | Discontinued; last supported version 1.12.2. |
| Rift | 1.13.x only, discontinued. |
| Risugami's ModLoader | Pre-Forge loader, discontinued well before 1.18. |
| Sponge | Server-side platform; no client for 1.18.2. |
| Bukkit / Spigot / Paper / Purpur | Server plugin platforms, no client. |
| Mohist / Arclight / Cardboard / Magma | Hybrid Forge+Bukkit server platforms, not client mod loaders. |
| Sinytra Connector | Compatibility layer that runs Fabric mods on Forge/NeoForge — a bridge, not a target loader. |
| NilLoader | Version-agnostic Java-agent loader, but exposes no registry or event API to hook and no model for declaring dependencies. |

## Layout

One Gradle build, one subproject per loader:

| Target | Artifact | Notes |
| --- | --- | --- |
| Forge | `forge/build/libs/registry-extractor-*-mojmap.jar` (+ `-dev`, `-prod`) | behaviour unchanged; sources moved under `forge/` |
| Fabric | `fabric/build/libs/registry-extractor-fabric-0.0.1-1.18.2.jar` | new, client-only entrypoint |
| Quilt | loads the Fabric jar as-is | no `quilt.mod.json`; QFAPI supplies `fabric-api` |

Both modules use **Mojang official mappings**, so the port is a loader-API delta rather than a rename
exercise.

### What was substituted for Forge-only APIs

| Output field | Forge source | Fabric substitute |
| --- | --- | --- |
| `inventorySize` (items) | `CapabilityItemHandler` on the stack | the item's block entity, when it implements vanilla `Container` → `getContainerSize()`; otherwise `0` |
| `feContainer` / `feCapacity` | `CapabilityEnergy` | no equivalent → `false` / `0` |
| `shearable` (blocks, entities) | `IForgeShearable` | no equivalent → `false` |
| `isPlantable` / `plantType` / `plant` (blocks, items) | `IPlantable` | no equivalent → `false` / `"none"` / `""` |
| `fuelBurnTime` | `ForgeHooks.getBurnTime` | burn time is resolved from recipes at runtime → `0` |
| `name` (fluids) | `FluidAttributes.getDisplayName` | the placed block's name, else the registry path |
| `density` / `temperature` / `viscosity` / `lighterThanAir` / `rarity` | `FluidAttributes` | absent from vanilla → `0` / `0` / `0` / `false` / `"COMMON"` |
| `sounds` (fluids) | `FluidAttributes.getFillSound`/`getEmptySound` | no equivalent → `null` |
| `fluid` (buckets) | `BucketItem.getFluid()` | reflective read of vanilla's private `content` field, `null` if it moves |

Every key is still emitted on both loaders, so the schema keeps one shape. (Forge 1.18.2 also writes
`sounds.vaporize` as `null`: `FluidAttributes` has no vaporize sound.)

Two 1.18.2 details worth knowing: registries are the static `net.minecraft.core.Registry.ITEM/BLOCK/FLUID/
ENTITY_TYPE` fields (`BuiltInRegistries` does not exist until 1.19.3), and Fabric API's command callback is
`net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback` with a `(dispatcher, dedicated)` signature —
the `v2` form with `registryAccess` arrives in 1.19.

## Build constraints discovered

- **One Gradle version for both plugins.** ForgeGradle 5.1 tops out at Gradle 7.x while Loom 1.x needs
  Gradle 8, so the shared build stays on Gradle 7.4.2 and Loom is pinned to **0.12.12**.
- **Stale Loom cache can look like a missing Minecraft jar.** A failed first Loom configure left a 22-byte
  `minecraft-project-@fabric-merged-named.jar` in `.gradle/loom-cache/1.18.2/…`; every compile then failed
  with `package net.minecraft.core does not exist`. Deleting `.gradle/loom-cache/1.18.2` and
  `~/.gradle/caches/fabric-loom/1.18.2/loom.mappings.*` and re-running fixed it — the mapped jar was
  rebuilt at 24 MB.
- Loom 0.12.6 failed remapping 1.19.2 with `Unsupported class file major version 63`; 0.12.12 is the newest
  Loom that runs on Gradle 7.4.2.

## Verified, and not

Verified: `./gradlew clean build :forge:mojmapJar` is green from scratch and produces all five jars; the
Fabric jar's `fabric.mod.json` parses with `version: 0.0.1-1.18.2` and `minecraft: 1.18.2`; the Forge mojmap
jar has zero SRG (`f_<n>_`) leftovers; and a dev Fabric client boots on 1.18.2 (`Loading Minecraft 1.18.2
with Fabric Loader 0.20.1`, `Loading 51 mods`), lists `registry_extractor 0.0.1-1.18.2` and runs the client
entrypoint (`Registry Extractor Initializing...`).

Not verified: an actual `/extract` run on Fabric (it needs a player in a world), and Quilt loading the jar —
there is no Quilt harness here, so that remains a documented expectation (`provides: fabric-api 0.67.0` is
read from QFAPI's own metadata, but loading the jar on Quilt is not measured).
