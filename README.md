# Vividly

Vividly is an active-liveness detection library for Android. It asks the person in front of the
camera to perform a short, randomly ordered sequence of actions — smile, blink, turn your head —
verifies each one from ML Kit's face classifier, and captures a photo at the instant the last
challenge is completed. The state machine that decides all of this is a pure Kotlin module with no
Android dependencies at all, so the interesting logic is unit-tested rather than device-tested; a
CameraX adapter and an optional Compose UI are layered on top and can be replaced independently.

> **Screenshot / GIF placeholder** — drop a recording of the sample app at `docs/demo.gif` and
> reference it here.
>
> `![Vividly demo](docs/demo.gif)`

---

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

| Module            | Artifact          | Depends on                    | Use it when |
|-------------------|-------------------|-------------------------------|-------------|
| `liveness-core`   | `vividly-core`    | kotlinx-coroutines-core       | You have your own face detector and want only the challenge logic |
| `liveness-camera` | `vividly-camera`  | CameraX, ML Kit face detection| You want the camera plumbing but your own UI |
| `liveness-compose`| `vividly-compose` | Compose, Material 3           | You want a screen that works out of the box |

## Quick start — Compose

```kotlin
dependencies {
    implementation("io.github.taimuur:vividly-compose:<version>")
}
```

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

- Passive liveness signals (texture, reflectance, micro-movement) to complement the challenges
- A head-nod challenge, using pitch as well as yaw
- Publishing to Maven Central
- `binary-compatibility-validator` for a locked public API surface

## Building

```bash
./gradlew :liveness-core:test    # the state machine's test suite
./gradlew assembleDebug          # all three libraries plus the sample app
```

Requires JDK 17 and an Android SDK with API 36. The `sample` module is the manual test bed.

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
