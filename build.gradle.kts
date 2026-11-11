plugins {
  java
  `maven-publish`
}

// One branch per Minecraft version; the version comes from gradle.properties (overridable with
// -Pminecraft_version=<mc>). The project version is what a release bumps, so it carries no
// Minecraft version of its own.
val mcVersion: String = property("minecraft_version") as String

version = "0.0.1"

group = "com.kirjorjos.registry-extractor"

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

// The jar prefix the release names every asset with; `.github/metadata.json` declares the same one.
val artifactPrefix = "RegistryExtractor"

val loaderModules = listOf("forge-mod")

fun loaderJarDir(module: String): File =
  project.file("src/${module.removeSuffix("-mod")}/mod/build/libs")

val artifactsVersion = "$mcVersion-$version"

tasks.register("buildAll") {
  group = "build"
  description = "Builds every loader's distributable jar (:forge-mod:productionJar)"
  dependsOn(loaderModules.map { ":$it:productionJar" })
}

/**
 * The jars a release attaches: the remapped jar a launcher loads, and its `-deobf` twin - the same
 * code in development names, for reading stack traces. Both are named the way the release check
 * expects (`<prefix><loader>-<mc>-<version><variant>.jar`), so the staged set and the assets agree.
 */
tasks.register<Copy>("collectArtifacts") {
  group = "build"
  description = "Builds every loader jar and copies them into build/artifacts"
  dependsOn("buildAll")

  loaderModules.forEach { module ->
    val loader = module.removeSuffix("-mod")
    val name = "$artifactPrefix-$loader-$artifactsVersion"
    from(loaderJarDir(module)) { include("$name.jar", "$name-deobf.jar") }
  }

  val artifactsDir = layout.buildDirectory.dir("artifacts").get().asFile
  into(artifactsDir)

  // A Copy only adds files, so a jar from another version or an older SHA256SUMS.txt would ride
  // along into the release. Starting empty makes the staged tree describe this invocation alone.
  doFirst { delete(artifactsDir) }
}

// The shared `build` check uploads `build/libs/*.jar` and treats an empty upload as a failure. The
// loader module's jars live at `src/forge/mod/build/libs`, which that path cannot see, so the ones a
// user installs are staged where the check looks.
tasks.register<Copy>("stageJars") {
  group = "build"
  description = "Stages the loader jars under build/libs for the shared build check"
  dependsOn("buildAll")

  loaderModules.forEach { module ->
    val loader = module.removeSuffix("-mod")
    from(loaderJarDir(module)) { include("$artifactPrefix-$loader-$artifactsVersion*.jar") }
  }

  into(layout.buildDirectory.dir("libs"))
}

// One version and group for the whole tree: a module's jar name carries the version a release bumps.
subprojects {
  group = rootProject.group
  version = rootProject.version
}

// The root project has no sources: its own (empty) jar would sit beside the staged ones.
tasks.named<Jar>("jar") { enabled = false }

tasks.named("build") { dependsOn("stageJars") }

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "registry-extractor"
      artifact(tasks.named("stageJars")) {
        builtBy(tasks.named("stageJars"))
      }
    }
  }
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/kirjorjos/RegistryExtractor")
      credentials {
        username = System.getenv("GITHUB_ACTOR") ?: "github-actions"
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}
