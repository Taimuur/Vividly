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

package io.github.taimuur.vividly.core

/**
 * Probability and angle thresholds used by the built-in challenge detectors.
 *
 * @property smile A face counts as smiling when `smilingProbability >= smile`.
 * @property eyeClosed An eye counts as closed when its open-probability is strictly below this
 *   value.
 * @property eyeOpen An eye counts as open when its open-probability is strictly above this value.
 *
 *   Together with [eyeClosed] this forms a hysteresis band (by default: closed below `0.3`, open
 *   above `0.7`). Probabilities that land inside the band leave the blink detector in whatever
 *   state it was already in, which stops a jittery detector from manufacturing phantom blinks.
 * @property turnAngleDegrees How far the head must rotate, in degrees, for a turn challenge to
 *   count. See [FaceFrame.headYawDegrees] for the sign convention.
 */
public data class DetectionThresholds(
    val smile: Float = 0.6f,
    val eyeClosed: Float = 0.3f,
    val eyeOpen: Float = 0.7f,
    val turnAngleDegrees: Float = 20f,
) {
    init {
        require(smile in 0f..1f) { "smile threshold must be in 0f..1f, was $smile" }
        require(eyeClosed in 0f..1f) { "eyeClosed threshold must be in 0f..1f, was $eyeClosed" }
        require(eyeOpen in 0f..1f) { "eyeOpen threshold must be in 0f..1f, was $eyeOpen" }
        require(eyeClosed < eyeOpen) {
            "eyeClosed ($eyeClosed) must be strictly below eyeOpen ($eyeOpen) to form a " +
                "hysteresis band"
        }
        require(turnAngleDegrees > 0f) { "turnAngleDegrees must be positive, was $turnAngleDegrees" }
    }
}

/**
 * Controls the short, verified "face the camera" stage between the last challenge and the capture.
 *
 * Without it, the photo is taken on the exact frame the final challenge passes. When that challenge
 * is a turn, the head is by definition rotated past
 * [DetectionThresholds.turnAngleDegrees] — so the captured image is a profile shot, which is
 * useless for review or face matching.
 *
 * This stage is deliberately *not* a delay. A blind pause between verification and shutter would
 * reopen the gap that automatic capture exists to close, letting a different face step in
 * unverified. Instead the session keeps checking during the hold: the same tracked face, alone in
 * frame, in a neutral pose. Anything else resets progress, just as it would mid-challenge.
 *
 * @property enabled Whether to wait for a neutral pose. Turning this off restores capture on the
 *   instant the last challenge passes.
 * @property maxYawDegrees How close to facing the camera the head must be, in degrees either way.
 * @property requireEyesOpen Whether the eyes must also be open, so the capture does not land
 *   mid-blink. Uses [DetectionThresholds.eyeOpen].
 * @property minConsecutiveFrames How many consecutive frames must satisfy the pose before the
 *   photo is taken.
 */
public data class CaptureHold(
    val enabled: Boolean = true,
    val maxYawDegrees: Float = 10f,
    val requireEyesOpen: Boolean = true,
    val minConsecutiveFrames: Int = 2,
) {
    init {
        require(maxYawDegrees > 0f) { "maxYawDegrees must be positive, was $maxYawDegrees" }
        require(minConsecutiveFrames >= 1) {
            "minConsecutiveFrames must be at least 1, was $minConsecutiveFrames"
        }
    }
}

/**
 * Configuration for a [LivenessSession].
 *
 * @property challenges The pool of challenges a session may draw from. Must not be empty and must
 *   not contain duplicates.
 * @property challengeCount How many challenges are drawn from [challenges] for one session. Must
 *   be in `1..challenges.size`.
 * @property randomizeOrder Whether the drawn challenges are shuffled for every session.
 *
 *   **Defaults to `true` deliberately.** A fixed challenge order is trivially replayable: an
 *   attacker who has watched one session knows exactly which recording to play back at the next
 *   one. Only turn this off for scripted UI tests.
 * @property thresholds Detector thresholds, see [DetectionThresholds].
 * @property challengeTimeoutMs How long the user may spend on a single challenge before the
 *   session fails with [FailureReason.CHALLENGE_TIMEOUT]. The clock restarts each time the session
 *   advances to the next challenge.
 * @property sessionTimeoutMs Total budget for the whole session, measured from the first processed
 *   frame, after which it fails with [FailureReason.SESSION_TIMEOUT]. This clock keeps running even
 *   while no face is visible, so a user cannot stall it by stepping out of view.
 * @property minConsecutiveFrames How many consecutive frames must satisfy a smile or turn challenge
 *   before it counts as passed. Values above `1` filter out single-frame detector noise.
 * @property captureHold The verified "face the camera" stage before the photo is taken, see
 *   [CaptureHold]. It gets its own [challengeTimeoutMs] budget, like any other step.
 */
public data class LivenessConfig(
    val challenges: List<Challenge> = Challenge.entries,
    val challengeCount: Int = 3,
    val randomizeOrder: Boolean = true,
    val thresholds: DetectionThresholds = DetectionThresholds(),
    val challengeTimeoutMs: Long = 15_000,
    val sessionTimeoutMs: Long = 60_000,
    val minConsecutiveFrames: Int = 2,
    val captureHold: CaptureHold = CaptureHold(),
) {
    init {
        require(challenges.isNotEmpty()) { "challenges must not be empty" }
        require(challenges.distinct().size == challenges.size) {
            "challenges must not contain duplicates, was $challenges"
        }
        require(challengeCount in 1..challenges.size) {
            "challengeCount must be in 1..${challenges.size}, was $challengeCount"
        }
        require(challengeTimeoutMs > 0) {
            "challengeTimeoutMs must be positive, was $challengeTimeoutMs"
        }
        require(sessionTimeoutMs > 0) { "sessionTimeoutMs must be positive, was $sessionTimeoutMs" }
        require(minConsecutiveFrames >= 1) {
            "minConsecutiveFrames must be at least 1, was $minConsecutiveFrames"
        }
    }
}
