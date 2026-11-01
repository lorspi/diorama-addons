plugins {
    id("dioaddons.neoforge-mod")
}

// claims-api is bundled inside this jar (Jar-in-Jar), so the claim layer classes always load. The claims mod
// itself is optional and never a compile dependency.
evaluationDependsOn(":claims-api")
evaluationDependsOn(":render-core")
val claimsApi = project(":claims-api")
val renderCore = project(":render-core")

dependencies {
    api(claimsApi)
    jarJar(claimsApi)
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

// Development only: ./gradlew :webmap:runServer -Pdioaddons.withClaims=true also loads the claims mod.
if (providers.gradleProperty("dioaddons.withClaims").isPresent) {
    evaluationDependsOn(":claims")
    val claimsProject = project(":claims")
    dependencies {
        runtimeOnly(claimsProject)
    }
    neoForge {
        mods {
            register("dioaddons_claims") {
                sourceSet(claimsProject.the<SourceSetContainer>()["main"])
            }
        }
    }
}
