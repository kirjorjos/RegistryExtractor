# Registry Extractor

A Minecraft 1.18.2 mod that walks a running modpack's registries and dumps everything it can observe about
the loaded content into a single JSON file. It builds for Forge and Fabric; Quilt loads the Fabric jar.

It is a data-extraction tool for development, not a gameplay mod. Its output feeds the [Unofficial
Integrated Dynamics Docs and Tools](https://github.com/kirjorjos/Unofficial-Integrated-Dynamics-Docs-and-Tools),
a TypeScript reimplementation of Integrated Dynamics' core logic that consumes it as typed game data. There
are no published releases yet; a release flow is planned, so today it runs from source.

## What it extracts

The root object holds `metadata` plus one object per registry — `items`, `blocks`, `fluids` and `entities` —
each keyed by namespace and then path, so `minecraft:stone` ends up at `blocks.minecraft.stone`.

**Items** — display name, owning mod, backing block, max stack size, max damage, enchantable flag, furnace
burn time, FE container flag and capacity, fluid container flag and fluid, inventory size, tool tier and dig
speed, plantable flag/type/plant, tooltip lines, vanilla translation key, tags.

**Blocks** — name, mod, item, opaque flag, shearable flag, break/place/step sounds, plantable info, crop max
age, blockstate properties with their possible values, fluid, destroy speed, harvest tier inferred from the
`minecraft:needs_*_tool` tags, vanilla translation key, tags.

**Fluids** — name, mod, block, light level, density, temperature, viscosity, lighter-than-air, rarity, bucket
empty/fill/vaporize sounds, bucket item, vanilla translation key, tags. Flowing fluids are skipped so each fluid is reported once.

**Entities** — name, mod, category-derived `mob`/`animal`/`player` flags, registry id, width, height, max
health, hurt and death sounds, shearable flag, vanilla translation key, tags, and the items the entity will eat (for animals).

On Fabric, data that only Forge can answer (energy and item-handler capabilities, `IPlantable`,
`IForgeShearable`, fuel burn time, fluid physical properties) is reported as its neutral value. The schema
is identical on both loaders; `docs/loaders-1.18.2.md` has the full substitution table.

## Usage

**This build requires JDK 17.** `gradle.properties` pins a local JDK 17, so `./gradlew` works without any
environment setup; change that line if your JDK 17 lives elsewhere.

The build is split per loader: `forge/` and `fabric/` are Gradle subprojects under one root build.

```bash
./gradlew build            # both loaders
./gradlew :forge:build     # Forge only; jars land in forge/build/libs
./gradlew :fabric:build    # Fabric only; jar lands in fabric/build/libs
```

Then run the mod in a client that is in a world, and use:

```
/extract ~/registry.json
```

The path is a greedy string, so spaces work without quoting. A leading `~` expands to the home directory, and
a path that resolves to a directory gets `registry.json` appended. The result is reported in chat as
`Wrote to: <absolute path>`. It needs a player in a world, so it cannot be run from the server console.

## License

[GPLv3](LICENSE.txt). Author: kirjorjos.
