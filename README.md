# Registry Extractor

A Forge mod for Minecraft 1.19.2 that walks a running modpack's registries and dumps everything it can
observe about the loaded content into a single JSON file.

It is a data-extraction tool for development, not a gameplay mod. Its output feeds the [Unofficial
Integrated Dynamics Docs and Tools](https://github.com/kirjorjos/Unofficial-Integrated-Dynamics-Docs-and-Tools),
a TypeScript reimplementation of Integrated Dynamics' core logic that consumes it as typed game data. There
are no published releases yet; a release flow is planned, so today it runs from source.

## What it extracts

The root object holds `metadata` plus one object per registry — `items`, `blocks`, `fluids` and `entities` —
each keyed by namespace and then path, so `minecraft:stone` ends up at `blocks.minecraft.stone`.

**Items** — display name, owning mod, backing block, max stack size, max damage, enchantable flag, furnace
burn time, FE container flag and capacity, fluid container flag and fluid, inventory size, tool tier and dig
speed, plantable flag/type/plant, tooltip lines, tags.

**Blocks** — name, mod, item, opaque flag, shearable flag, break/place/step sounds, plantable info, crop max
age, blockstate properties with their possible values, fluid, destroy speed, harvest tier inferred from the
`minecraft:needs_*_tool` tags, tags.

**Fluids** — name, mod, block, light level, density, temperature, viscosity, lighter-than-air, rarity, bucket
empty/fill/vaporize sounds, bucket item, tags. Flowing fluids are skipped so each fluid is reported once.

**Entities** — name, mod, category-derived `mob`/`animal`/`player` flags, registry id, width, height, max
health, hurt and death sounds, shearable flag, tags, and the items the entity will eat (for animals).

## Usage

**This build requires JDK 17.** `gradle.properties` pins a local JDK 17, so `./gradlew` works without any
environment setup; change that line if your JDK 17 lives elsewhere.

```bash
./gradlew build      # jars land in build/libs
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
