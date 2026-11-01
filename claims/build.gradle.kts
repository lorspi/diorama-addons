plugins {
    id("dioaddons.neoforge-mod")
}

// claims-api is bundled inside this jar (Jar-in-Jar) and loaded as its own mod in development runs.
evaluationDependsOn(":claims-api")
evaluationDependsOn(":render-core")
val claimsApi = project(":claims-api")
val renderCore = project(":render-core")

dependencies {
    api(claimsApi)
    jarJar(claimsApi)
    // Terrain shading for the claims screen.
    implementation(renderCore)
    jarJar(renderCore)
}

neoForge {
    mods {
        register("dioaddons_claims_api") {
            sourceSet(claimsApi.the<SourceSetContainer>()["main"])
        }
        register("dioaddons_render_core") {
            sourceSet(renderCore.the<SourceSetContainer>()["main"])
        }
    }
}
