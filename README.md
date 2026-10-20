# Vividly

[![Maven Central](https://img.shields.io/maven-central/v/io.github.taimuur/vividly-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.taimuur/vividly-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://developer.android.com/tools/releases/platforms)

Active-liveness detection for Android. Vividly asks the person in front of the camera to perform a
short, randomly ordered set of actions — smile, blink, turn your head — and automatically captures a
photo once they are facing the camera again. You get back a verified selfie, or a reason it failed.

- **Drop-in or bring your own UI.** One composable, or a camera controller, or just the state
  machine.
- **Runs on-device.** No server, no account, no network calls of its own, nothing uploaded.
- **Themable and localisable.** Every string and visual is a slot you can replace.

> **Know what it is before you ship it.** Vividly is lightweight *active* liveness, not certified
> anti-spoofing. It deters casual spoofing; it does not stop a determined attacker. See
> [Security](#security) to decide whether it fits your use case.

---

## Requirements

| | |
|---|---|
| **Android** | API 21+ |
| **Device** | A front-facing camera, and **Google Play services** (see below) |
| **Built with** | `compileSdk` 36, Kotlin 2.4, CameraX 1.6, Compose BOM 2026.06 |
| **Permission** | `CAMERA` — declared by the library, but *you* request it at runtime |

**Google Play services is required.** Face detection uses ML Kit's *unbundled* model, which is
delivered by Google Play services rather than packaged in your APK. This keeps your app small, but
it means the check depends on Play services being present. Devices without it (for example Huawei
devices without GMS, or AOSP builds) are not supported and are expected to report a
`LivenessCaptureResult.Error`. This has not been tested on such a device, so verify on your own
target hardware if it matters to you. The library asks Play services to download the model ahead of
first use, but a session started before the download finishes can be slow or fail until it
completes. Plan a fallback path for any device that returns an `Error`.

## Installation

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.taimuur:vividly-compose:0.2.0")
}
```

That is the whole setup. Maven Central is in the default repository list for a standard Android
project. If your `settings.gradle.kts` replaces the default repositories, make sure both `google()`
and `mavenCentral()` are listed.

Depend on a lower level if you do not want the bundled UI:

| Dependency | What you get |
|---|---|
| `io.github.taimuur:vividly-compose:0.2.0` | The ready-made Compose screen, plus everything below |
| `io.github.taimuur:vividly-camera:0.2.0`  | CameraX and ML Kit wiring; bring your own UI |
| `io.github.taimuur:vividly-core:0.2.0`    | The state machine only; bring your own face detector |

No ProGuard/R8 rules are needed; the consumer rules ship with the libraries.

## Quick start

A complete flow: ask for the camera, run the check, handle the outcome, allow a retry.

```kotlin
@Composable
fun VerifyScreen(onVerified: (File) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    // Changing this key rebuilds the check from scratch with a freshly drawn challenge order.
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        // Your own explanation / "open settings" UI goes here.
        return
    }

    key(attempt) {
        VividlyLiveness(
            modifier = Modifier.fillMaxSize(),
            onResult = { result ->
                when (result) {
                    is LivenessCaptureResult.Success -> onVerified(result.photoFile)
                    is LivenessCaptureResult.Failure -> attempt++      // or show a message first
                    is LivenessCaptureResult.Error   -> reportError(result.cause)
                }
            },
        )
    }
}
```

The [`sample`](sample) module is a runnable version of this, including a result screen.

### Camera permission is your job

Vividly ships **no permission UI**. `android.permission.CAMERA` is declared in the library manifest
and merged into your app, but *requesting* it is yours to do, so the prompt fits your own
onboarding and copy. If the permission is missing when the check starts, you get a
`LivenessCaptureResult.Error`.

## Handling the result

`onResult` is called exactly once per run, on the main thread.

| Result | Meaning | What to do |
|---|---|---|
| `Success(photoFile, completedChallenges)` | The user completed every challenge and a photo was captured. | Upload or use `photoFile`, then delete it. |
| `Failure(reason)` | The check ended without completing. | Offer a retry. |
| `Error(cause)` | Something outside the check failed: camera could not start, permission missing, no Play services, photo could not be written. | Log `cause`; show a generic error or a fallback path. |

`FailureReason` is one of:

| Reason | When |
|---|---|
| `CHALLENGE_TIMEOUT` | The user spent too long on one challenge. |
| `SESSION_TIMEOUT` | The whole session ran out of time. |
| `CANCELLED` | The check was cancelled (for example the user backed out). |

Treat `Failure` and `Error` differently: a `Failure` means the *user* did not pass, an `Error` means
the *device or setup* could not run the check.

### The photo file

`photoFile` is a JPEG written to your app's **cache directory**, and **you own it**. Copy or upload
it promptly and delete it afterwards — the system may clear the cache at any time, and files are
never removed for you. To choose a different location, use the headless API below, which accepts an
`outputDirectory`; `VividlyLiveness` does not expose it.

Vividly itself makes no network calls and requests no `INTERNET` permission. The photo goes
nowhere until you send it.

## Customizing the UI

Everything visual in `VividlyLiveness` is a slot. Override `prompts` to localise or reword, or
`overlay` to replace the UI entirely:

```kotlin
VividlyLiveness(
    onResult = ::handle,
    prompts = { state -> stringResource(myPromptRes(state)) },
    overlay = { state -> MyBrandedOverlay(state) },
)
```

`state` is a `SessionState`: `SearchingForFace`, `MultipleFaces`, `ChallengeInProgress(challenge,
index, total)`, `AwaitingCapture`, and the terminal `Succeeded` / `Failed`. That is everything you
need to draw your own progress UI.

## Configuration

Pass a `LivenessConfig` to tune the check. It validates itself on construction, so a bad
combination fails immediately rather than at runtime.

```kotlin
VividlyLiveness(
    config = LivenessConfig(
        challenges = listOf(Challenge.SMILE, Challenge.BLINK),
        challengeCount = 2,
        sessionTimeoutMs = 45_000,
    ),
    onResult = ::handle,
)
```

| Option | Type | Default | Meaning |
|---|---|---|---|
| `challenges` | `List<Challenge>` | all four | Pool to draw from. Non-empty, no duplicates. |
| `challengeCount` | `Int` | `3` | How many are drawn per session. Must be in `1..challenges.size`. |
| `randomizeOrder` | `Boolean` | `true` | Shuffle the drawn challenges. **Leave this on** — see [Security](#security). |
| `thresholds` | `DetectionThresholds` | see below | Detector cut-offs. |
| `challengeTimeoutMs` | `Long` | `15_000` | Budget per challenge; the clock restarts on each advance. |
| `sessionTimeoutMs` | `Long` | `60_000` | Budget for the whole session, from the first frame. |
| `minConsecutiveFrames` | `Int` | `2` | Frames a smile or turn must persist for, to filter noise. |
| `captureHold` | `CaptureHold` | see below | The pose check before the photo is taken. |

| Challenge | Passes when |
|---|---|
| `SMILE` | The user smiles and holds it briefly. |
| `BLINK` | The user closes and reopens their eyes. |
| `TURN_LEFT` | The user turns their head to *their own* left. |
| `TURN_RIGHT` | The user turns their head to *their own* right. |

<details>
<summary>Detection thresholds</summary>

| Threshold | Type | Default | Meaning |
|---|---|---|---|
| `smile` | `Float` | `0.6f` | Smiling when `smilingProbability >= smile`. |
| `eyeClosed` | `Float` | `0.3f` | An eye is closed below this. |
| `eyeOpen` | `Float` | `0.7f` | An eye is open above this. The gap between the two is a hysteresis band. |
| `turnAngleDegrees` | `Float` | `20f` | How far the head must rotate for a turn to count. |

Raise `smile` or `turnAngleDegrees` to make the check stricter; lower them if users on older or
low-light devices struggle to pass.

</details>

<details>
<summary>Capture hold (why the photo isn't taken instantly)</summary>

A turn challenge ends with the head rotated away from the lens, so capturing the instant it passes
would produce a profile shot. `CaptureHold` waits for the user to face the camera again. It is a
*verified* wait, not a delay: the same face must stay in frame, alone, throughout, and losing or
swapping it resets the session exactly as it would mid-challenge.

| Option | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Set `false` to capture the instant the last challenge passes. |
| `maxYawDegrees` | `Float` | `10f` | How close to facing the camera the head must be, either way. |
| `requireEyesOpen` | `Boolean` | `true` | Avoids capturing mid-blink. Uses `eyeOpen`. |
| `minConsecutiveFrames` | `Int` | `2` | Frames the pose must hold before the photo is taken. |

</details>

## Using your own UI or detector

Using the camera or core modules without Compose is a first-class path, not an afterthought.

### Headless: your UI, Vividly's camera

Depend on `vividly-camera`, drive `LivenessCameraController`, and render `state` however you like:

```kotlin
val controller = LivenessCameraController(
    context = context,
    config = LivenessConfig(challengeCount = 2),
    outputDirectory = myDirectory,   // optional; defaults to the cache directory
    onResult = ::handleResult,
)

lifecycleScope.launch {
    controller.bind(lifecycleOwner, previewView.surfaceProvider)
    controller.state.collect { render(it) }
}

// on teardown, or call controller.cancel() to end early with FailureReason.CANCELLED
controller.stop()
```

### Core only: your own face detector

Depend on `vividly-core` and feed the state machine yourself from any detector. This module is pure
Kotlin/JVM with no Android dependencies, so it also works without Play services.

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

## How it works

Three modules, each usable on its own. Dependencies point downward only, and each level exposes the
one below it with `api`, so depending on `vividly-compose` also gives you the camera and core types.

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

| Artifact          | Depends on                     |
|-------------------|--------------------------------|
| `vividly-core`    | kotlinx-coroutines-core        |
| `vividly-camera`  | CameraX, ML Kit face detection |
| `vividly-compose` | Compose, Material 3            |

The `sample` module is a demo app and is not published.

### Session rules

- **Terminal states absorb.** Once a session succeeds or fails, further frames do nothing until
  `reset()`. No late frame can flip the outcome.
- **Losing the face resets progress** to the first challenge. So does a second face appearing, and
  so does the tracking id changing. One person cannot start a session and another finish it.
- **The session clock keeps running** while no face is visible, so stepping out of frame is not a
  way to stall.
- **The photo is taken after a brief pose check**, not on the frame the last challenge passes. The
  session keeps verifying throughout, so this is a wait, not a gap.

### Challenge details

| Challenge | Exact condition |
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

### Design decisions that exist for security reasons

Worth preserving if you fork:

- `randomizeOrder` defaults to `true`. A fixed script is a replayable script.
- The photo is captured **automatically**. If a user had to tap a button afterwards, nothing would
  bind the captured image to the face that passed the challenges. `CaptureHold` waits for a usable
  pose first, but keeps verifying while it waits — it is deliberately not a blind delay, which would
  reopen exactly the gap automatic capture exists to close.
- `BLINK` requires the eyes to *reopen*. Accepting "eyes are closed now" would let a photograph of
  someone with their eyes shut walk straight through.
- Progress is destroyed, not paused, whenever the tracked face is lost or replaced.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Error` result straight away | `CAMERA` permission not granted, no front camera, or Google Play services unavailable. Inspect `result.cause`. |
| `Error` even though the user just granted the permission | `VividlyLiveness` was already on screen when the permission was granted, so the camera had already failed to bind. Show it only once the permission is granted, as in the quick start. |
| Stuck on "searching for face" | The face is out of frame, too dark, or covered. The session clock keeps running and will time out. |
| Stuck on "multiple faces" | More than one face is in view. The session waits until exactly one remains. |
| Passes too easily / too hard | Adjust `thresholds`, `challengeCount` or `challengeTimeoutMs` — see [Configuration](#configuration). |

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to build
and test the project, including building against local source, and [RELEASING.md](RELEASING.md) for
the release process.

## License

Apache-2.0, free for commercial use. See [LICENSE](LICENSE).
