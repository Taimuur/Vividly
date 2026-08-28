plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.taimuur.vividly.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":liveness-camera"))

    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // PreviewView: needed by the default UI, but not by a headless integration, so it stays out of
    // the camera module's dependencies.
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}

mavenPublishing {
    // Coordinates come from project.group / project.version so that -Pgroup and -Pversion still
    // override them; see gradle.properties and jitpack.yml.
    coordinates(group.toString(), "vividly-compose", version.toString())

    publishToMavenCentral()

    // Signing is required by Maven Central but must not be required for `publishToMavenLocal` or
    // for JitPack, neither of which has a key. Enabling it only when one is configured keeps those
    // paths working on a machine with no GPG setup at all.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("vividly-compose")
        description.set(
            "Optional slot-based Compose UI for Vividly: camera preview, prompts and a " +
                "replaceable overlay.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/Taimuur/Vividly")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Taimuur")
                name.set("Taimur Hassan")
                url.set("https://github.com/Taimuur")
            }
        }
        scm {
            url.set("https://github.com/Taimuur/Vividly")
            connection.set("scm:git:https://github.com/Taimuur/Vividly.git")
            developerConnection.set("scm:git:ssh://git@github.com/Taimuur/Vividly.git")
        }
    }
}
