plugins { id("xyz.wagyourtail.unimined") }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

val mcVersion = property("minecraft_version") as String

// The compatible range the mod metadata declares, from the same single source of truth: "1.18.2"
// accepts 1.18.x, "26.1" accepts 26.1.x. Hardcoding the range in mods.toml is how a build reports a
// plausible-looking wrong version once the branch moves.
val mcNext =
  mcVersion.split('.').let { parts ->
    if (parts.size >= 2) "${parts[0]}.${parts[1].toInt() + 1}" else mcVersion
  }

// The one licence identifier in the tree; mods.toml declares `${licence}` and processResources fills
// it in, so the metadata cannot drift from LICENSE.txt.
val licence = property("licence") as String

// The Forge line this version branch builds against; mods.toml accepts anything from 43 upwards.
val forgeVersion = "43.5.0"

unimined.minecraft {
  version(mcVersion)
  mappings { mojmap() }
  minecraftForge { loader(forgeVersion) }
  // The plain `jar` below is the development view (Mojmap names) that a dev client can load;
  // `productionJar` is that same code remapped into Forge's production namespace.
  defaultRemapJar = false
}

repositories { mavenCentral() }

dependencies {
  // ForgeGradle put jsr305 on the compile classpath by itself; unimined's Minecraft classpath does
  // not carry it, and ExtractFluids imports `javax.annotation.Nullable`.
  compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

// Resources live beside the module rather than under src/main/resources, the layout Minecheck uses.
sourceSets.main.get().resources.srcDir(project.file("resources"))

tasks.processResources {
  inputs.property("version", project.version)
  inputs.property("minecraftVersion", mcVersion)
  inputs.property("minecraftNext", mcNext)
  inputs.property("licence", licence)
  // mods.toml declares `${version}` once and this is the only place it is filled in; the old
  // `${file.jarVersion}` required a manifest attribute to exist or the mod loaded as version 0.0NONE.
  filesMatching("META-INF/mods.toml") {
    expand(
      "version" to project.version.toString(),
      "minecraft_version" to mcVersion,
      "minecraft_next" to mcNext,
      "licence" to licence,
    )
  }
}

val jarBaseName = "RegistryExtractor-forge"
val jarVersion = "$mcVersion-${project.version}"

tasks.jar {
  archiveBaseName.set(jarBaseName)
  archiveVersion.set(jarVersion)
  // The development view of the mod. The release attaches it beside the remapped jar.
  archiveClassifier.set("deobf")
}

// unimined's remap targets the plain `jar`, which is disabled for redistribution here because the
// jar a launcher loads has to carry Forge's `m_12345_` / `f_12345_` names: a jar built against
// Mojmap names runs in a dev client and dies in a production instance.
val minecraftConfig =
  extensions
    .getByType(xyz.wagyourtail.unimined.api.UniminedExtension::class.java)
    .minecrafts[sourceSets.main.get()]
    ?: error("unimined registered no minecraft config for the main source set")

val productionJar =
  minecraftConfig.remap(tasks.jar.get(), "productionJar") {
    asJar {
      archiveBaseName.set(jarBaseName)
      archiveVersion.set(jarVersion)
      archiveClassifier.set("")
    }
  }

tasks.named("assemble") { dependsOn(productionJar) }
