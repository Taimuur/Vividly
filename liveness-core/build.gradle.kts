plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifactId = "vividly-core"

            pom {
                name.set("vividly-core")
                description.set(
                    "The Vividly liveness state machine: challenge sequencing, detectors and " +
                        "timeouts, with no Android dependencies.",
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
