# Security policy

Vividly is a liveness-detection library, so reports about ways to bypass or weaken a check are
taken seriously.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem. Report it privately through GitHub's
[private vulnerability reporting](https://github.com/Taimuur/Vividly/security/advisories/new)
("Security" tab → "Report a vulnerability").

Include what you found, how to reproduce it, and which version you tested. You can expect an
acknowledgement within a few days. Fixes ship in a new release; published versions on Maven Central
are immutable, so a fix always means a higher version number.

## What counts

In scope:

- A way to make a session succeed without the challenges being performed, or to swap the verified
  face for another between verification and capture (a flaw in the state machine or capture flow).
- A way to read or leak the captured photo from outside the host application.

Out of scope, and documented in the README's [Security](README.md#security) section:

- Defeating the check with a live screen replay, a deepfake or a physical mask. Vividly is
  lightweight active liveness, not certified anti-spoofing, and does not claim to stop these.
- Weaknesses in ML Kit, CameraX or Google Play services themselves; report those upstream.

## Supported versions

Only the latest released version receives fixes.
