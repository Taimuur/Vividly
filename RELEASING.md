# Releasing

Maintainer notes. Consumers do not need anything in this file.

## Cutting a release

Three steps. Bump the version in [`gradle.properties`](gradle.properties), sync the docs to it, then
tag:

```bash
./gradlew syncReadmeVersion
```

```bash
git commit -am "chore: prepare 0.1.1" && git tag 0.1.1 && git push origin main 0.1.1
```

Pushing the tag runs [`.github/workflows/publish.yml`](.github/workflows/publish.yml), which checks
the docs, tests, builds, signs and releases to Maven Central, then attaches the artifacts to a
GitHub release.

`gradle.properties` is the single source of truth for the version. `syncReadmeVersion` rewrites
every `io.github.taimuur:vividly-*:<version>` coordinate in the README from it, so the install
snippet and the dependency table cannot go stale. The workflow runs `checkReadmeVersion` before it
builds anything and fails if they have drifted from the tag — so forgetting the sync stops the
release rather than shipping instructions that point at an older version.

The tag must match the version being released. It reaches Gradle as `-Pversion` with any leading `v`
stripped, so `0.1.1` and `v0.1.1` both work. The workflow can also be run manually from the Actions
tab with an explicit version.

**Maven Central releases are immutable.** A published version can never be replaced or deleted, only
superseded by a higher one — which is why the test suite gates the publish step. A run that fails
before publishing does *not* consume the version number; Central only claims a version on a
successful release, so a failed attempt can be retried with the same number.

After the workflow finishes, the deployment sits in `PUBLISHING` on the Portal for a few minutes
while Central syncs to `repo1.maven.org`. Artifacts are usually resolvable within ~30 minutes; the
search index can lag by a few hours. To check:

```bash
curl -o /dev/null -w "%{http_code}\n" https://repo1.maven.org/maven2/io/github/taimuur/vividly-core/0.1.0/vividly-core-0.1.0.pom
```

Note that the *directory* listing for the group returns 404 on `repo1` even when the files are
present — check a file, not a directory.

## Required repository secrets

Settings → Secrets and variables → Actions → Repository secrets:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token name |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `VIVIDLY_ACTION_SECRET` | GPG private key, ASCII-armoured or base64-encoded |
| `SIGNING_PASSWORD` | Passphrase for that key |

The workflow checks all four before building and names any that are missing, because an unset secret
expands to an empty string and would otherwise fail several minutes later with an unrelated-looking
error.

Note that secrets added under the **Dependabot** tab, or as **Environment** secrets, are invisible
to workflows — they must be repository secrets on the Actions tab.

## Rotating the signing key

Generate a key, publish the public half to a keyserver (Central verifies signatures against it), and
export the private half:

```bash
gpg --full-generate-key
```

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

```bash
gpg --export-secret-keys --armor YOUR_KEY_ID | base64 -w0
```

The result goes into `VIVIDLY_ACTION_SECRET`. On Windows, `gpg` ships with Git for Windows at
`C:\Program Files\Git\usr\bin\gpg.exe` but is not on the Windows `PATH`.

## Publishing by hand

Useful for a dry run. Credentials go in `~/.gradle/gradle.properties` — never in this repository:

```properties
mavenCentralUsername=<Central Portal user token name>
mavenCentralPassword=<Central Portal user token password>
signingInMemoryKey=<ASCII-armoured GPG private key, newlines as \n>
signingInMemoryKeyPassword=<its passphrase>
```

```bash
./gradlew publishToMavenCentral            # upload, then release manually in the Portal
```

```bash
./gradlew publishAndReleaseToMavenCentral  # upload and release in one step
```

`publishToMavenCentral` leaves a deployment you can inspect and drop in the Portal without releasing
it, which validates signatures, checksums and POM completeness without consuming a version number.

Signing is enabled only when `signingInMemoryKey` is present, so `publishToMavenLocal` and JitPack
builds keep working on a machine with no GPG setup at all.
