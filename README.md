# Vividly

[![Maven Central](https://img.shields.io/maven-central/v/io.github.taimuur/vividly-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.taimuur/vividly-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://developer.android.com/tools/releases/platforms)

Vividly is an active-liveness detection library for Android. It asks the person in front of the
camera to perform a short, randomly ordered sequence of actions — smile, blink, turn your head —
verifies each one from ML Kit's face classifier, and captures a photo at the instant the last
challenge is completed. The state machine that decides all of this is a pure Kotlin module with no
Android dependencies at all, so the interesting logic is unit-tested rather than device-tested; a
CameraX adapter and an optional Compose UI are layered on top and can be replaced independently.

<!-- Record the sample app and drop it in as docs/demo.gif, then uncomment:
![Vividly demo](docs/demo.gif)
-->

---

## Installation

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.taimuur:vividly-compose:0.1.0")
}
```

That is the whole setup. Maven Central is in the default repository list for a standard Android
project, so no extra repository declaration is needed.

`vividly-compose` brings the rest with it. Depend on a lower level instead if you do not want the
bundled UI:

| Dependency | What you get |
|---|---|
| `io.github.taimuur:vividly-compose:0.1.0` | The ready-made Compose screen, plus everything below |
| `io.github.taimuur:vividly-camera:0.1.0`  | CameraX and ML Kit wiring; bring your own UI |
| `io.github.taimuur:vividly-core:0.1.0`    | The state machine only; bring your own face detector |

<details>
<summary>If your build overrides the repository list</summary>

Some projects replace the defaults in `settings.gradle.kts`. Make sure both of these are present —
`google()` is needed for CameraX, Compose and ML Kit:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

</details>

<details>
<summary>Consuming an unreleased commit (JitPack)</summary>

Released versions come from Maven Central. To try a commit that has not been released yet, JitPack
builds this repository on demand — note its free tier only builds **public** repositories.

```kotlin
// settings.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

```kotlin
implementation("com.github.Taimuur.Vividly:vividly-compose:main-SNAPSHOT")
```

The group differs from the Maven Central one because JitPack resolves a multi-module build under
`com.github.<User>.<Repo>`; [`jitpack.yml`](jitpack.yml) overrides the group for those builds.

</details>

<details>
<summary>Building against local source</summary>

Useful when modifying the library itself. Clone this repository next to your app and point
`settings.gradle.kts` at the module directories:

```kotlin
include(":liveness-core", ":liveness-camera", ":liveness-compose")

project(":liveness-core").projectDir = file("../vividly/liveness-core")
project(":liveness-camera").projectDir = file("../vividly/liveness-camera")
project(":liveness-compose").projectDir = file("../vividly/liveness-compose")
```

The library modules declare their dependencies through this repository's version catalog, so also
copy the entries they reference from [`gradle/libs.versions.toml`](gradle/libs.versions.toml) into
your own catalog — otherwise the build fails with `Unresolved reference: libs`.

Alternatively, run `./gradlew publishToMavenLocal` here and depend on it from `mavenLocal()`, using
whatever version [`gradle.properties`](gradle.properties) currently declares.

</details>

## Modules

```
┌─────────────────┐
│ liveness-compose│  vividly-compose  — optional default UI (slot-based, themable)
└────────┬────────┘
         │ api
┌────────▼────────┐
│ liveness-camera │  vividly-camera   — CameraX + ML Kit adapter, auto-capture
└────────┬────────┘
         │ api
┌────────▼────────┐
│  liveness-core  │  vividly-core     — the state machine. Pure Kotlin/JVM, zero Android
└─────────────────┘
```

Dependencies point downward only. Because each level exposes the one below it with `api`, depending
on `vividly-compose` also gives you the camera and core types.

| Gradle module     | Artifact          | Depends on                     |
|-------------------|-------------------|--------------------------------|
| `liveness-core`   | `vividly-core`    | kotlinx-coroutines-core        |
| `liveness-camera` | `vividly-camera`  | CameraX, ML Kit face detection |
| `liveness-compose`| `vividly-compose` | Compose, Material 3            |

A fourth module, `sample`, is a demo app rather than a library, and is not published.

## Quick start — Compose

```kotlin
VividlyLiveness(
    modifier = Modifier.fillMaxSize(),
    onResult = { result ->
        when (result) {
            is LivenessCaptureResult.Success -> upload(result.photoFile)
            is LivenessCaptureResult.Failure -> showRetry(result.reason)
            is LivenessCaptureResult.Error   -> reportError(result.cause)
        }
    },
)
```

Everything visual is a slot. Override `prompts` to localise, or `overlay` to replace the UI
entirely:

```kotlin
VividlyLiveness(
    onResult = ::handle,
    prompts = { state -> getString(myPromptRes(state)) },
    overlay = { state -> MyBrandedOverlay(state) },
)
```

### Camera permission is the host's job

Vividly ships **no permission UI**. `android.permission.CAMERA` is declared in the library manifest
and merged into your app — because the library cannot function without it — but *requesting* it at
runtime is yours to do, so the prompt fits your own onboarding and copy:

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted -> /* ... */ }
```

If the permission is missing, the camera fails to bind and you get a
`LivenessCaptureResult.Error`. See the `sample` module for the full flow.

## Quick start — headless

Using core plus camera with your own UI is a first-class path, not an afterthought. Drive
`LivenessCameraController` and render `state` however you like:

```kotlin
val controller = LivenessCameraController(
    context = context,
    config = LivenessConfig(challengeCount = 2),
    outputDirectory = myDirectory,
    onResult = ::handleResult,
)

lifecycleScope.launch {
    controller.bind(lifecycleOwner, previewView.surfaceProvider)
    controller.state.collect { render(it) }
}

// on teardown
controller.stop()
```

Or go further and skip the camera module too — feed the state machine yourself from any detector:

```kotlin
val session = LivenessSession(LivenessConfig())

// from your own analyzer, on any thread:
session.process(
    FaceFrame(
        timestampMs = SystemClock.elapsedRealtime(),
        faceCount = faces.size,
        trackingId = face.trackingId,
        smilingProbability = face.smilingProbability,
        leftEyeOpenProbability = face.leftEyeOpenProbability,
        rightEyeOpenProbability = face.rightEyeOpenProbability,
        headYawDegrees = face.headEulerAngleY,
    ),
)
```

`LivenessSession` takes its time from `FaceFrame.timestampMs` and its randomness from an injectable
`Random`, so a full session — timeouts included — can be simulated in a unit test without a clock
or a camera.

## Configuration reference

`LivenessConfig` validates itself in `init`, so a bad combination fails loudly at construction
rather than silently at runtime.

| Option | Type | Default | Meaning |
|---|---|---|---|
| `challenges` | `List<Challenge>` | all four | Pool to draw from. Non-empty, no duplicates. |
| `challengeCount` | `Int` | `3` | How many are drawn per session. Must be in `1..challenges.size`. |
| `randomizeOrder` | `Boolean` | `true` | Shuffle the drawn challenges. **Leave this on** — see Security. |
| `thresholds` | `DetectionThresholds` | see below | Detector cut-offs. |
| `challengeTimeoutMs` | `Long` | `15_000` | Budget per challenge; the clock restarts on each advance. |
| `sessionTimeoutMs` | `Long` | `60_000` | Budget for the whole session, from the first frame. |
| `minConsecutiveFrames` | `Int` | `2` | Frames a smile or turn must persist for, to filter noise. |

| Threshold | Type | Default | Meaning |
|---|---|---|---|
| `smile` | `Float` | `0.6f` | Smiling when `smilingProbability >= smile`. |
| `eyeClosed` | `Float` | `0.3f` | An eye is closed below this. |
| `eyeOpen` | `Float` | `0.7f` | An eye is open above this. The gap between the two is a hysteresis band. |
| `turnAngleDegrees` | `Float` | `20f` | How far the head must rotate for a turn to count. |

### Challenges

| Challenge | Passes when |
|---|---|
| `SMILE` | `smilingProbability` is above threshold for `minConsecutiveFrames` in a row |
| `BLINK` | A full **open → closed → open** transition is observed on the same tracked face |
| `TURN_LEFT` | `headYawDegrees >= +turnAngleDegrees` for `minConsecutiveFrames` in a row |
| `TURN_RIGHT` | `headYawDegrees <= -turnAngleDegrees` for `minConsecutiveFrames` in a row |

**Yaw sign convention:** positive means the user turned their head to *their own* left. Preview
surfaces mirror the front camera, so this is also the direction the user sees themselves move on
screen. The analysis frames are not mirrored, and ML Kit reports a positive `headEulerAngleY` when
the face turns toward the right of the processed image — the same physical direction — so the value
passes through unchanged.

### Session rules worth knowing

- **Terminal states absorb.** Once a session succeeds or fails, further frames do nothing until
  `reset()`. No late frame can flip the outcome.
- **Losing the face resets progress** to the first challenge. So does a second face appearing, and
  so does the tracking id changing. One person cannot start a session and another finish it.
- **The session clock keeps running** while no face is visible, so stepping out of frame is not a
  way to stall.

## Security

Read this before shipping Vividly into anything that matters.

**What it is.** Lightweight *active* liveness: challenge-response driven by ML Kit's face
classification. It raises the cost of the laziest attacks. A still photo cannot smile, cannot blink
on demand, and cannot turn its head; a recording made before the session started cannot know which
of the randomly ordered challenges will be asked, or in which order.

**What it is not.** Vividly is **not certified anti-spoofing**. Specifically:

- No ISO/IEC 30107-3 presentation-attack-detection testing, and no iBeta certification.
- No depth sensing, no texture or moiré analysis, no reflectance or challenge-illumination checks,
  no passive liveness signals of any kind.
- Detection rests entirely on ML Kit's smile / eye-open / head-angle probabilities, which are
  convenience classifiers, not anti-spoofing measures.

**A determined attacker will defeat it.** A screen replay driven in real time, a sufficiently good
deepfake, or a physical mask will pass these challenges. Randomising the order raises the bar for
pre-recorded replays; it does nothing against an attacker who can respond live.

**Use it for** low-stakes verification UX: confirming a real person is present during onboarding,
deterring casual account sharing, adding friction to bulk automated signups.

**Do not use it** as the sole control for high-security KYC, financial account opening, or anything
where a successful spoof is expensive. For those, pair it with a certified PAD vendor, server-side
verification, or a human review step. Vividly runs entirely on-device, and anything running entirely
on the client can be lied to.

**Design decisions that exist for security reasons** — worth preserving if you fork:

- `randomizeOrder` defaults to `true`. A fixed script is a replayable script.
- The photo is captured **automatically**, the instant the session succeeds. If a user had to tap a
  button afterwards, nothing would bind the captured image to the face that passed the challenges.
- `BLINK` requires the eyes to *reopen*. Accepting "eyes are closed now" would let a photograph of
  someone with their eyes shut walk straight through.
- Progress is destroyed, not paused, whenever the tracked face is lost or replaced.

## Roadmap

**None of the following is implemented.** This section exists so you can tell the difference
between a deliberate omission and a known gap.

**Passive liveness signals.** Vividly today is purely *active*: it only knows you are real because
you performed actions on request. Passive checks — skin texture versus screen pixels, reflectance,
involuntary micro-movement — inspect the image itself and need no cooperation. The two complement
each other, because a live screen replay can answer challenges but tends to fail texture and
reflectance checks.

**A head-nod challenge.** The turn challenges use *yaw*, rotation left and right
(`headEulerAngleY`). A nod is *pitch*, up and down (`headEulerAngleX`). Adding it means a new field
on `FaceFrame`, a new detector, and a new `Challenge` entry. More distinct challenges means a larger
pool for the randomiser to draw from, which makes a session harder to pre-record.

**`binary-compatibility-validator`.** A Gradle plugin that dumps the public API surface into `.api`
files committed to the repository, then fails the build whenever a change alters that surface. Now
that the artifacts are published, deleting a public function or changing a signature breaks a
consumer's compile — or throws `NoSuchMethodError` at runtime for anyone who does not rebuild. The
validator turns an accidental break into a visible diff in code review. It pairs with the
`explicitApi()` mode already enabled here: `explicitApi()` forces you to *declare* what is public,
the validator *locks* what is public.

## Building

```bash
./gradlew :liveness-core:test    # the state machine's test suite (39 tests)
./gradlew assembleDebug          # every module
./gradlew publishToMavenLocal    # the three library artifacts, into ~/.m2
```

`assembleDebug` produces the two Android library AARs and the sample APK, and builds
`liveness-core` along the way — core is a plain JVM module with no `assembleDebug` task of its own,
but the Android modules depend on it, so its jar is built as part of the same command. In short, one
command covers all four modules.

Requires JDK 17 and an Android SDK with API 36 installed. The `sample` module is the manual test
bed: it exercises the runtime permission grant, a successful capture, and each failure mode.

## Releasing

**Releases are automated.** Pushing a version tag runs
[`.github/workflows/publish.yml`](.github/workflows/publish.yml), which tests, builds, signs and
releases to Maven Central, then attaches the artifacts to a GitHub release:

```bash
git tag 0.1.1 && git push origin 0.1.1
```

Before tagging, bump the version in the [Installation](#installation) snippet and table above — the
badge tracks the latest release automatically, but those copy-paste examples are hardcoded and will
otherwise send people to an older version.

After the workflow finishes, the deployment sits in `PUBLISHING` on the Portal for a few minutes
while Central syncs it to `repo1.maven.org`. Artifacts are usually resolvable within ~30 minutes;
the search index can lag by a few hours.

The tag is the source of truth for the version — it is passed to Gradle as `-Pversion`, with any
leading `v` stripped, so `0.1.1` and `v0.1.1` both work and the published version can never drift
from the tag. The value in [`gradle.properties`](gradle.properties) is only the default for local
builds. The workflow can also be run manually from the Actions tab with an explicit version.

Note that **Maven Central releases are immutable**: a published version cannot be replaced or
deleted, only superseded. That is why the test suite gates the publish step.

### Required repository secrets

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token name |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `VIVIDLY_ACTION_SECRET` | GPG private key, ASCII-armoured or base64-encoded |
| `SIGNING_PASSWORD` | Passphrase for that key |

### Publishing by hand

The same thing works locally. Put the credentials in `~/.gradle/gradle.properties` — **never** in
this repository:

```properties
mavenCentralUsername=<Central Portal user token name>
mavenCentralPassword=<Central Portal user token password>
signingInMemoryKey=<ASCII-armoured GPG private key, newlines as \n>
signingInMemoryKeyPassword=<its passphrase>
```

```bash
./gradlew publishToMavenCentral            # upload, then release manually in the Portal
./gradlew publishAndReleaseToMavenCentral  # upload and release in one step
```

Signing is enabled only when `signingInMemoryKey` is present, so `publishToMavenLocal` and JitPack
builds keep working on a machine with no GPG setup at all.

## License

Apache-2.0. See [LICENSE](LICENSE).

New source files should carry the standard header:

```kotlin
/*
 * Copyright 2026 The Vividly Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```
