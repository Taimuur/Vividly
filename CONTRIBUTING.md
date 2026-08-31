# Contributing

Thanks for your interest in Vividly. Bug reports, feature requests and pull requests are all
welcome.

## Requirements

- JDK 17
- Android SDK with API 36 installed

## Building

```bash
./gradlew :liveness-core:test    # the state machine's test suite
./gradlew assembleDebug          # every module
```

`assembleDebug` produces the two Android library AARs and the sample APK, and builds
`liveness-core` along the way — core is a plain JVM module with no `assembleDebug` task of its own,
but the Android modules depend on it, so its jar is built as part of the same command. One command
covers all four modules.

## Project layout

| Module | What it is |
|---|---|
| `liveness-core` | The state machine. Pure Kotlin/JVM, no Android dependencies. |
| `liveness-camera` | CameraX and ML Kit adapter. |
| `liveness-compose` | Optional Compose UI. |
| `sample` | Demo app and manual test bed. Not published. |

Dependencies point downward only: `compose → camera → core`. Nothing in a library module may depend
on an app-specific concern (image loaders, analytics, fonts, brand colours, navigation) — those
belong to the host application.

## Where to put a change

**`liveness-core` is where the interesting logic lives, and it is fully unit-testable.** It takes
its time from `FaceFrame.timestampMs` and its randomness from an injectable `Random`, so an entire
session — timeouts, face loss, a substituted face — can be simulated without a device, a camera or a
clock. If you are adding detection behaviour, it almost certainly belongs there, with tests, rather
than in the camera or Compose layers.

The `sample` module is the manual test bed for everything that cannot be unit-tested: the runtime
permission grant, a real capture, and each failure mode.

## Conventions

- **Kotlin only**, with `explicitApi()` enabled on all three published modules. Public declarations
  need explicit visibility and explicit return types; the build fails otherwise.
- **Apache-2.0 header** on every new source file — copy one from an existing file.
- Public API changes need KDoc explaining not just what a thing does but why it behaves that way,
  particularly where the behaviour is a security property.

## Security-relevant behaviour

Several design decisions exist specifically to make spoofing harder. If you are changing any of
these, please say so explicitly in the pull request:

- `randomizeOrder` defaults to `true` — a fixed challenge script is a replayable one.
- The photo is captured automatically the instant the session succeeds, so nothing can be
  substituted between verification and shutter.
- `BLINK` requires a full open → closed → open transition; accepting "eyes are closed now" would let
  a photograph of closed eyes pass.
- Progress is destroyed, not paused, when the tracked face is lost or replaced.

## Roadmap

Not implemented, and open to contribution:

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
consumer's compile — or throws `NoSuchMethodError` at runtime for anyone who does not rebuild. It
pairs with the `explicitApi()` mode already enabled here: `explicitApi()` forces you to *declare*
what is public, the validator *locks* what is public.
