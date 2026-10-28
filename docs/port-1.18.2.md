# 1.18.2 port notes

Branch `1.18.2/development`, cut from `1.19.2/development` per the porting recipe in
`.notes/branching-versioning-migration-spec.md` §10.1.

## Status

| Check | Result |
|---|---|
| `./gradlew clean build mojmapJar` | **green** — `BUILD SUCCESSFUL`, 13 tasks executed, exit 0 |
| Jar identity | 4 jars, all `registry-extractor-0.0.1-1.18.2*`; manifest `Implementation-Version: 0.0.1-1.18.2` |
| Mappings | 0 SRG leftovers (`f_[0-9]+_`) in the mojmap jar |
| Mod loads on 1.18.2 | **yes** — `Registry Extractor Initializing...`, `HELLO FROM COMMON SETUP`, `HELLO FROM CLIENT SETUP`, then texture atlases built (main menu) |
| `/extract` writes valid JSON | **not verified** — see Blockers |
| Minecheck suite | **cannot run** — Minecheck is pinned to 1.19.2 |

## Toolchain

| Setting | 1.19.2 | 1.18.2 |
|---|---|---|
| Forge | `1.19.2-43.5.0` | `1.18.2-40.3.12` |
| Mappings | `official` `1.19.2` | `official` `1.18.2` |
| `mods.toml` loaderVersion / forge dep | `[43,)` | `[40,)` |
| `mods.toml` minecraft range | `[1.19.2,1.20)` | `[1.18.2,1.19)` |
| `pack.mcmeta` | resource 9 / data 10 | resource **8** / data **9** |
| `version` | `0.0.1-1.19.2` | `0.0.1-1.18.2` |

Gradle 7.4.2 and JDK 17 are unchanged and are what ForgeGradle 5 for 1.18.2 expects.

`gameVersion` in the exported JSON is *derived* from `mods.toml` at runtime
(`ExtractCommand#gameVersion`), so nothing had to hardcode `1.18.2` — the metadata move alone flips it.

## Real API deltas

Only three, all in the extraction classes. Everything else (`ForgeRegistries.ITEMS/BLOCKS/FLUIDS`,
`builtInRegistryHolder().tags()`, `IPlantable`, `IForgeShearable`, capability lookups, `ModList`) is
source-compatible between 1.18.2 and 1.19.2.

1. **`Component.literal(String)` → `new TextComponent(String)`** (`ExtractCommand`).
   `Component.literal` arrives in 1.19; 1.18.2 only has `TextComponent`. `sendSuccess`/`sendFailure` take
   a `Component` in both, so only construction changed.

2. **`ForgeRegistries.ENTITY_TYPES` → `ForgeRegistries.ENTITIES`** (`ExtractEntities`).
   Same registry, 1.19-era rename. Verified against the 1.18.2 forge jar: `IForgeRegistry<EntityType<?>> ENTITIES`.

3. **`FluidType` + `SoundActions` → `FluidAttributes`** (`ExtractFluids`).
   1.18.2 Forge has no `FluidType` class and no `SoundActions`; `fluid.getAttributes()` returns
   `FluidAttributes`, which has `getDensity/getTemperature/getViscosity/isLighterThanAir/getRarity`
   as no-arg methods (same values, no `FluidStack` argument needed).

### Data delta to know about

`FluidAttributes` exposes `getFillSound()` / `getEmptySound()` but **no vaporize sound**; the 1.19.2 code
read `SoundActions.FLUID_VAPORIZE`. The key is kept — `"vaporize": null` — so the JSON schema stays
identical and the consumer sees a null instead of a missing field. If the consumer requires a string
there, that is a genuine cross-version data difference, not a port bug.

## Reference material for emulation semantics

Before treating any API difference as a blocker, check the operators that already encode how Integrated
Dynamics reads these registries:

```
/home/kirjorjos/git/IntegratedMods/BaseMods/IntegratedDynamics/src/main/java/org/cyclops/integrateddynamics/core/evaluate/operator/Operators.java
```

That checkout is **1.19.2 / Forge 43.0.8** (`BaseMods/IntegratedDynamics/gradle.properties`), so its
`ForgeRegistries.*` patterns (`getKey`, `getValue`, `tags().getReverseTag`, `getTagKeys()`) are
1.19.2-era — good for *what the data means* to the consumer, not a compile oracle for 1.18.2. It is where
the block/item/fluid/entity operators live (item + block name/id, tags, physical properties, entity
type id) and it was consulted for the fluid and entity fields here.

## Blockers / not done

- **Extraction run not performed.** Minecheck drives the harness and is pinned to `1.19.2/v1.0.0`
  (`tests/minecheck/run.sh` defaults `EXPECTED_VERSION=1.19.2`), so `tests/minecheck/` cannot validate
  this branch. The `/extract` command path in a world — the part that actually writes `registry.json` —
  is therefore unproven here. It needs either a Minecheck port to 1.18.2 or a manual client run.
- **Not promoted.** `1.18.2/main` does not exist yet; per §10.1 step 6 that comes from a promotion PR
  after this branch is validated.
- **`.notes/remaining-versions.json` untouched.** §10.1 step 7 removes the version only on promotion,
  which has not happened.
- `gradle.properties` still hardcodes `org.gradle.java.home` (migration decision 30) — inherited,
  unchanged, still CI-hostile.

## Operational gotcha

The first `./gradlew build` on this branch hung for ~10 minutes inside ForgeGradle's `downloadServer`
step with no socket open; killing it and re-running completed the setup normally (completed artifacts are
cached, so a retry resumes rather than restarts). Expect this on any first-time version switch.
