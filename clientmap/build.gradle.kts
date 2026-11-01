plugins {
    id("dioaddons.neoforge-mod")
}

repositories {
    // Diorama is only published on Modrinth.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

evaluationDependsOn(":claims-api")
evaluationDependsOn(":claims")
val claimsApi = project(":claims-api")
val claimsProject = project(":claims")
val dioramaVersion = project.property("diorama_version").toString()

dependencies {
    // Bundled: the server side reads claims through it whether or not the claims mod is installed.
    api(claimsApi)
    jarJar(claimsApi)
    // Optional integrations, never bundled. Code using them is in classes loaded only when they are present.
    compileOnly(claimsProject)
    compileOnly("maven.modrinth:diorama-map:$dioramaVersion")
}

neoForge {
    mods {
        register("dioaddons_claims_api") {
            sourceSet(claimsApi.the<SourceSetContainer>()["main"])
        }
    }
}

// Development only: -Pdioaddons.withClaims=true and/or -Pdioaddons.withDiorama=true add them to the runs.
if (providers.gradleProperty("dioaddons.withClaims").isPresent) {
    dependencies { runtimeOnly(claimsProject) }
    neoForge { mods { register("dioaddons_claims") { sourceSet(claimsProject.the<SourceSetContainer>()["main"]) } } }
}
if (providers.gradleProperty("dioaddons.withDiorama").isPresent) {
    dependencies { runtimeOnly("maven.modrinth:diorama-map:$dioramaVersion") }
}
