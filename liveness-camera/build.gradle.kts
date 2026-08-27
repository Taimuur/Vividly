plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "io.github.taimuur.vividly.camera"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":liveness-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    implementation(libs.mlkit.face.detection)
}

// The Android release component only exists once the variants have been created.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vividly-camera"

                pom {
                    name.set("vividly-camera")
                    description.set(
                        "CameraX and ML Kit adapter for Vividly: feeds face frames into the " +
                            "liveness state machine and captures a photo on success.",
                    )
                    url.set("https://github.com/Taimuur/Vividly")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
        }
    }
}
