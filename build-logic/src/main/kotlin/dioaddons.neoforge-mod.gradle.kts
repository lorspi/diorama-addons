// Shared conventions for every Diorama Addons mod module.
// Each module sets `mod_suffix`, `mod_name`, `mod_version` and `mod_description`
// in its own gradle.properties.
plugins {
    `java-library`
    id("net.neoforged.moddev")
}

fun prop(name: String): String = project.property(name).toString()

val modId = "${prop("mod_id_prefix")}_${prop("mod_suffix")}"
val javaVersion = prop("java_version").toInt()

group = prop("mod_group")
version = prop("mod_version")
base.archivesName.set("${rootProject.name}-${project.name}-${prop("minecraft_version")}")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

repositories {
    mavenCentral()
}

neoForge {
    version = prop("neo_version")

    parchment {
        minecraftVersion = prop("parchment_minecraft_version")
        mappingsVersion = prop("parchment_mappings_version")
    }

    runs {
        register("client") {
            client()
            gameDirectory.set(project.file("run/client"))
            // Optional: ./gradlew :claims:runClient -Pdioaddons.join=127.0.0.1:25565 -Pdioaddons.username=Tester
            providers.gradleProperty("dioaddons.join").orNull?.let {
                programArgument("--quickPlayMultiplayer")
                programArgument(it)
            }
            providers.gradleProperty("dioaddons.username").orNull?.let {
                programArgument("--username")
                programArgument(it)
            }
        }
        register("server") {
            server()
            gameDirectory.set(project.file("run/server"))
            programArgument("--nogui")
        }
        register("gameTestServer") {
            type.set("gameTestServer")
            gameDirectory.set(project.file("run/gametest"))
        }
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            logLevel.set(org.slf4j.event.Level.INFO)
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    // Plain JUnit tests may use Minecraft's libraries (DataFixerUpper, Gson). Tests must not bootstrap the game.
    addModdingDependenciesTo(sourceSets.test.get())
}

// Accept the EULA for the local development server only.
tasks.matching { it.name == "runServer" }.configureEach {
    val eula = project.file("run/server/eula.txt")
    doFirst {
        eula.parentFile.mkdirs()
        eula.writeText("eula=true\n")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

val metadataProps = mapOf(
    "mod_id" to modId,
    "mod_name" to prop("mod_name"),
    "mod_version" to prop("mod_version"),
    "mod_description" to prop("mod_description"),
    "mod_license" to prop("mod_license"),
    "mod_authors" to prop("mod_authors"),
    "mod_id_prefix" to prop("mod_id_prefix"),
    "minecraft_version" to prop("minecraft_version"),
    "neo_version_range" to prop("neo_version_range"),
)

tasks.processResources {
    // Local copy so the lambda does not capture the script object (configuration cache).
    val props = metadataProps
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.jar {
    val licenseName = "LICENSE_$modId"
    from(rootProject.file("LICENSE")) { rename { licenseName } }
    manifest.attributes(
        "Implementation-Title" to prop("mod_name"),
        "Implementation-Version" to prop("mod_version"),
    )
}
