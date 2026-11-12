plugins {
  java
  `maven-publish`
}

// One branch per Minecraft version; the version comes from gradle.properties (overridable with
// -Pminecraft_version=<mc>). The project version is what a release bumps, so it carries no
// Minecraft version of its own.
val mcVersion: String = property("minecraft_version") as String

version = "0.0.5"

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

// The shared `lifecycle` check runs one task per loader, named `test<Loader>All`, and uploads
// `src/<loader>/mod/run` as that leg's artifact. The suite itself is a Minecheck checkout - it
// drives a real client - so this task owns the build and hands the harness the deobf jar: a nested
// `./gradlew` against *this* project would contend for the locks of the invocation that started it.
val deobfJar =
  layout.projectDirectory.file(
    "src/forge/mod/build/libs/$artifactPrefix-forge-$artifactsVersion-deobf.jar")

tasks.register<Exec>("testForgeAll") {
  group = "verification"
  description = "Runs the Minecheck lifecycle suite against the deobf extractor jar"
  dependsOn(":forge-mod:jar")

  workingDir = projectDir
  executable = "bash"
  args("tests/minecheck/run.sh")
  environment("LOADER", "forge")
  environment("EXPECTED_VERSION", mcVersion)
  // Skips the harness's own build step, which cannot run a second Gradle build over this project.
  environment("EXTRACTOR_JAR", deobfJar.asFile.absolutePath)
  environment("REPORT_DIR", "src/forge/mod/run")
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "registry-extractor"
      // `stageJars` is a `Copy` task, and a task's artifact is its output *directory*; Gradle refuses
      // to publish that (`Invalid publication 'mavenJava': artifact file is a directory`). Name the
      // two jars it stages instead, with a classifier on the `-deobf` twin so both fit under the one
      // artifact id. `builtBy` still makes the publication depend on the staging copy.
      loaderModules.forEach { module ->
        val loader = module.removeSuffix("-mod")
        val staged = "$artifactPrefix-$loader-$artifactsVersion"
        artifact(file("build/libs/$staged.jar")) { builtBy(tasks.named("stageJars")) }
        artifact(file("build/libs/$staged-deobf.jar")) {
          classifier = "deobf"
          builtBy(tasks.named("stageJars"))
        }
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
