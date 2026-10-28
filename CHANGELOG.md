# Changelog

<!--
Pending release notes. Write the entries for the *next* release below this comment.

The release workflow copies everything after the title and this comment into the GitHub Release
body, and aborts the release if there is nothing there. Once the release is published it resets
this file back to this header on the development branch — published notes live in the GitHub
Release, so this file never accumulates version sections.

Suggested shape, delete what you do not use:

## Added
-

## Changed
-

## Fixed
-
-->

## Added

- `/extract <path>` walks the running game's registries and writes one JSON document with `metadata` plus
  `items`, `blocks`, `fluids` and `entities`, each keyed by namespace and then path, so `minecraft:stone`
  lands at `blocks.minecraft.stone`.
- Items: display name, owning mod, backing block, max stack size, max damage, enchantable flag, furnace burn
  time, FE container flag and capacity, fluid container flag and fluid, inventory size, tool tier and dig
  speed, plantable flag/type/plant, tooltip lines, tags.
- Blocks: name, mod, item, opaque and shearable flags, break/place/step sounds, plantable info, crop max age,
  blockstate properties with their possible values, fluid, destroy speed, harvest tier inferred from the
  `minecraft:needs_*_tool` tags, tags.
- Fluids: name, mod, block, light level, density, temperature, viscosity, lighter-than-air flag, rarity,
  bucket empty/fill/vaporize sounds, bucket item, tags. Flowing fluids are skipped, so each fluid appears
  once.
- Entities: name, mod, category-derived `mob`/`animal`/`player` flags, registry id, width, height, max
  health, hurt and death sounds, shearable flag, tags, and the items an animal will eat.
- `metadata.schemaVersion` and `metadata.gameVersion`. The latter is derived from `mods.toml`'s Minecraft
  dependency range instead of being hardcoded, so a build cannot report a plausible-looking wrong version.

## Changed

- Path arguments are friendly: `~` expands to the home directory, a path that resolves to a directory gets
  `registry.json` appended, spaces need no quoting, and the result is reported in chat as
  `Wrote to: <absolute path>`.

## Notes

- For Minecraft 1.19.2 on Forge 43.5.0, built with official mappings and JDK 17.
- Client-side only: the command needs a player in a world, so it cannot be run from a server console.
- Not a gameplay mod. The output feeds the
  [Unofficial Integrated Dynamics Docs and Tools](https://github.com/kirjorjos/Unofficial-Integrated-Dynamics-Docs-and-Tools).
