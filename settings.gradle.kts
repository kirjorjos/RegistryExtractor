pluginManagement {
  repositories {
    maven("https://maven.wagyourtail.xyz/releases")
    maven("https://maven.wagyourtail.xyz/snapshots")
    mavenCentral()
    gradlePluginPortal()
  }
}

// unimined replaces ForgeGradle: it owns the mappings, the Minecraft classpath, the dev runs and the
// remap that turns the development jar into the one a launcher loads.
plugins { id("xyz.wagyourtail.unimined") version "1.4.1" apply false }

rootProject.name = "RegistryExtractor"

// One module per loader, at the path the shared CI assumes (`src/<loader>/mod`).
include(":forge-mod")

project(":forge-mod").projectDir = file("src/forge/mod")
