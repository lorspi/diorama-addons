plugins {
    id("dioaddons.neoforge-mod")
}

// claims-api is bundled inside this jar (Jar-in-Jar) and loaded as its own mod in development runs.
evaluationDependsOn(":claims-api")
val claimsApi = project(":claims-api")

dependencies {
    api(claimsApi)
    jarJar(claimsApi)
}

neoForge {
    mods {
        register("dioaddons_claims_api") {
            sourceSet(claimsApi.the<SourceSetContainer>()["main"])
        }
    }
}
