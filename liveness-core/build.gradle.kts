plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
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

mavenPublishing {
    // Coordinates come from project.group / project.version so that -Pgroup and -Pversion still
    // override them; see gradle.properties and jitpack.yml.
    coordinates(group.toString(), "vividly-core", version.toString())

    publishToMavenCentral()

    // Signing is required by Maven Central but must not be required for `publishToMavenLocal` or
    // for JitPack, neither of which has a key. Enabling it only when one is configured keeps those
    // paths working on a machine with no GPG setup at all.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("vividly-core")
        description.set(
            "The Vividly liveness state machine: challenge sequencing, detectors and timeouts, " +
                "with no Android dependencies.",
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
