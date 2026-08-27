# Vividly

Vividly is an active-liveness detection library for Android. It asks the person in front of the
camera to perform a short, randomly ordered sequence of actions — smile, blink, turn your head —
verifies each one from ML Kit's face classifier, and captures a photo at the instant the last
challenge is completed. The state machine that decides all of this is a pure Kotlin module with no
Android dependencies at all, so the interesting logic is unit-tested rather than device-tested; a
CameraX adapter and an optional Compose UI are layered on top and can be replaced independently.

> **Status: pre-release.** Vividly is not on Maven Central yet. It installs from
> [JitPack](#option-1-jitpack-recommended), which builds directly from a tag in this repository —
> but JitPack's free tier only builds **public** repositories, so while this one is private use the
> [source install](#option-2-as-source) instead. The API is not frozen and may change without
> notice.

> **Screenshot / GIF placeholder** — drop a recording of the sample app at `docs/demo.gif` and
> reference it here.
>
> `![Vividly demo](docs/demo.gif)`

---

## Modules

```
┌─────────────────┐
│ liveness-compose│  optional default UI (slot-based, themable)
└────────┬────────┘
         │ api
┌────────▼────────┐
│ liveness-camera │  CameraX + ML Kit adapter, auto-capture
└────────┬────────┘
         │ api
┌────────▼────────┐
│  liveness-core  │  the state machine. Pure Kotlin/JVM, zero Android
└─────────────────┘
```

Dependencies point downward only. Because each level exposes the one below it with `api`, depending
on `liveness-compose` also gives you the camera and core types.

| Gradle module     | Depends on                     | Use it when |
|-------------------|--------------------------------|-------------|
| `liveness-core`   | kotlinx-coroutines-core        | You have your own face detector and want only the challenge logic |
| `liveness-camera` | CameraX, ML Kit face detection | You want the camera plumbing but your own UI |
| `liveness-compose`| Compose, Material 3            | You want a screen that works out of the box |

A fourth module, `sample`, is a demo app rather than a library. It is not meant to be consumed.

Each library module publishes as `vividly-core`, `vividly-camera` and `vividly-compose`. The group
depends on where you get them from: `com.github.Taimuur.Vividly` via JitPack, and
`io.github.taimuur` once they reach Maven Central.

## Installation

### Option 1: JitPack (recommended)

JitPack builds this repository on demand and serves the result as a Maven artifact, so no Maven
Central release is needed. **It only works while the repository is public** — the free tier cannot
clone private repositories.

Add the JitPack repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then depend on a tag (or a commit hash, or `main-SNAPSHOT`):

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.Taimuur.Vividly:vividly-compose:0.1.0")
}
```

`vividly-compose` brings `vividly-camera` and `vividly-core` with it. For a headless integration,
depend on `vividly-camera` instead; for the state machine alone, `vividly-core`.

To cut a release, tag a commit and push the tag — the first request for that version is what
triggers the JitPack build:

```bash
git tag 0.1.0 && git push origin 0.1.0
```

### Option 2: as source

Works regardless of repository visibility, and is the better choice if you intend to modify the
library. Clone this repository next to your app, then point your `settings.gradle.kts` at the three
library directories:

```kotlin
// settings.gradle.kts — in your app
include(":liveness-core", ":liveness-camera", ":liveness-compose")

project(":liveness-core").projectDir = file("../vividly/liveness-core")
project(":liveness-camera").projectDir = file("../vividly/liveness-camera")
project(":liveness-compose").projectDir = file("../vividly/liveness-compose")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":liveness-compose"))
}
```

**One step that is easy to miss:** the library modules declare their dependencies through this
repository's version catalog (`libs.plugins.android.library`, `libs.androidx.camera.core`, and so
on). A build that does not define those aliases fails with `Unresolved reference: libs`. Copy the
entries the modules reference from [`gradle/libs.versions.toml`](gradle/libs.versions.toml) into
your own catalog, or replace the aliases with literal coordinates in your copy of the modules.

If you would rather not merge catalogs, the simplest starting point is to work inside this
repository and use the `sample` module as your app.

### Option 3: Maven Central — not yet available

Once the artifacts are released to Maven Central, no custom repository will be needed at all:

```kotlin
dependencies {
    implementation("io.github.taimuur:vividly-compose:<version>")
}
```

This does not resolve today. See [Roadmap](#roadmap).

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

**Publishing to Maven Central.** Maven Central is the default repository Gradle downloads
dependencies from, so an artifact there needs no extra repository declaration and no build step on
the consumer's side. [JitPack](#option-1-jitpack-recommended) already covers the practical need —
the modules carry full `maven-publish` configuration and POM metadata, and `publishToMavenLocal`
produces the same artifacts JitPack serves. What Maven Central adds is durability and reach: JitPack
builds on demand from a repository that must stay public, whereas Central hosts immutable artifacts
independent of this repository's existence or visibility. It also requires verified ownership of the
`io.github.taimuur` namespace, which is why the group differs between the two channels.

**`binary-compatibility-validator`.** A Gradle plugin that dumps the public API surface into `.api`
files committed to the repository, then fails the build whenever a change alters that surface. Once
someone depends on a published `vividly-core`, deleting a public function or changing a signature
breaks their compile — or throws `NoSuchMethodError` at runtime for anyone who does not rebuild. The
validator turns an accidental break into a visible diff in code review. It pairs with the
`explicitApi()` mode already enabled here: `explicitApi()` forces you to *declare* what is public,
the validator *locks* what is public. It is only worth adding once there are downstream consumers to
protect.

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

`publishToMavenLocal` is exactly what [`jitpack.yml`](jitpack.yml) runs, so publishing locally and
consuming `0.1.0-SNAPSHOT` from `mavenLocal()` reproduces what a JitPack consumer would get. Pass
`-Pversion=<something>` to override the version stamped into the artifacts.

Requires JDK 17 and an Android SDK with API 36 installed. The `sample` module is the manual test
bed: it exercises the runtime permission grant, a successful capture, and each failure mode.

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
