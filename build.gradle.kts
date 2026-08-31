plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// --- Documentation versioning -----------------------------------------------------------------
//
// The README shows copy-pasteable Gradle coordinates, so the version is written out in several
// places. Left to a human those go stale silently, and a stale install snippet sends people to an
// older release than the badge advertises.
//
// `syncReadmeVersion` rewrites them from the project version; `checkReadmeVersion` fails if they
// have drifted. The publish workflow runs the check before releasing, so a forgotten bump stops the
// release instead of shipping wrong instructions.
//
// Everything the task actions touch is captured as a local at configuration time. Referencing a
// script-level property from inside doLast would capture the script object itself, which the
// configuration cache cannot serialize.

tasks.register("syncReadmeVersion") {
    group = "documentation"
    description = "Rewrites the library coordinates in the docs to the current project version."

    // Matches the version in an `io.github.taimuur:vividly-<module>:<version>` coordinate.
    val pattern = """(io\.github\.taimuur:vividly-[a-z]+:)([0-9][A-Za-z0-9.\-]*)"""
    val docs = listOf(rootProject.file("README.md"))
    val releaseVersion = version.toString()

    doLast {
        val regex = Regex(pattern)
        var changedAny = false

        docs.forEach { doc ->
            val original = doc.readText()
            val updated = regex.replace(original) { match -> match.groupValues[1] + releaseVersion }
            if (original != updated) {
                doc.writeText(updated)
                changedAny = true
                logger.lifecycle("Updated ${doc.name} to $releaseVersion")
            }
        }

        if (!changedAny) {
            logger.lifecycle("Docs already reference $releaseVersion; nothing to do.")
        }
    }
}

tasks.register("checkReadmeVersion") {
    group = "verification"
    description = "Fails if the docs reference a version other than the current project version."

    val pattern = """(io\.github\.taimuur:vividly-[a-z]+:)([0-9][A-Za-z0-9.\-]*)"""
    val docs = listOf(rootProject.file("README.md"))
    val releaseVersion = version.toString()

    doLast {
        val regex = Regex(pattern)
        val stale = mutableListOf<String>()

        docs.forEach { doc ->
            regex.findAll(doc.readText())
                .map { it.groupValues[2] }
                .filter { it != releaseVersion }
                .distinct()
                .forEach { stale += "${doc.name} references $it" }
        }

        if (stale.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Docs are out of sync with the project version ($releaseVersion):")
                    stale.forEach { appendLine("  - $it") }
                    appendLine()
                    append("Run ./gradlew syncReadmeVersion and commit the result.")
                },
            )
        }

        logger.lifecycle("Docs reference $releaseVersion.")
    }
}
