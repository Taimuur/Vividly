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
 * Frame builders and a tiny session driver, so that the tests below read as a description of what
 * the user did rather than as a wall of constructor arguments.
 */

internal const val PRIMARY_FACE = 7

/** One neutral, clearly visible face: not smiling, eyes open, looking straight ahead. */
internal fun face(
    atMs: Long,
    trackingId: Int? = PRIMARY_FACE,
    smiling: Float? = 0.05f,
    leftEyeOpen: Float? = 0.95f,
    rightEyeOpen: Float? = 0.95f,
    yaw: Float? = 0f,
): FaceFrame = FaceFrame(
    timestampMs = atMs,
    faceCount = 1,
    trackingId = trackingId,
    smilingProbability = smiling,
    leftEyeOpenProbability = leftEyeOpen,
    rightEyeOpenProbability = rightEyeOpen,
    headYawDegrees = yaw,
)

internal fun smilingFace(atMs: Long, trackingId: Int? = PRIMARY_FACE): FaceFrame =
    face(atMs, trackingId = trackingId, smiling = 0.95f)

internal fun eyesOpenFace(atMs: Long, trackingId: Int? = PRIMARY_FACE): FaceFrame =
    face(atMs, trackingId = trackingId, leftEyeOpen = 0.95f, rightEyeOpen = 0.95f)

internal fun eyesClosedFace(atMs: Long, trackingId: Int? = PRIMARY_FACE): FaceFrame =
    face(atMs, trackingId = trackingId, leftEyeOpen = 0.05f, rightEyeOpen = 0.05f)

internal fun turnedLeftFace(atMs: Long, trackingId: Int? = PRIMARY_FACE): FaceFrame =
    face(atMs, trackingId = trackingId, yaw = 35f)

internal fun turnedRightFace(atMs: Long, trackingId: Int? = PRIMARY_FACE): FaceFrame =
    face(atMs, trackingId = trackingId, yaw = -35f)

/** No face in frame at all. */
internal fun noFace(atMs: Long): FaceFrame = FaceFrame(timestampMs = atMs, faceCount = 0)

/** The user plus a bystander. */
internal fun twoFaces(atMs: Long): FaceFrame = FaceFrame(
    timestampMs = atMs,
    faceCount = 2,
    trackingId = PRIMARY_FACE,
    smilingProbability = 0.95f,
    leftEyeOpenProbability = 0.95f,
    rightEyeOpenProbability = 0.95f,
    headYawDegrees = 0f,
)

/** The zero-based index of the challenge currently being asked, or `-1` if none is. */
internal fun LivenessSession.currentIndex(): Int =
    (state.value as? SessionState.ChallengeInProgress)?.index ?: -1

/** The challenge currently being asked. Fails the test if the session is not asking for one. */
internal fun LivenessSession.currentChallenge(): Challenge {
    val current = state.value
    check(current is SessionState.ChallengeInProgress) {
        "expected a challenge to be in progress, but the session was $current"
    }
    return current.challenge
}

/** The frames, in order, that satisfy [challenge]; repeated if more frames are needed. */
private fun framesFor(challenge: Challenge): List<(Long, Int?) -> FaceFrame> = when (challenge) {
    Challenge.SMILE -> listOf(::smilingFace)
    Challenge.BLINK -> listOf(::eyesOpenFace, ::eyesClosedFace, ::eyesOpenFace)
    Challenge.TURN_LEFT -> listOf(::turnedLeftFace)
    Challenge.TURN_RIGHT -> listOf(::turnedRightFace)
}

/**
 * Feeds frames that satisfy [challenge] until the session moves on, and returns the timestamp to
 * carry into the next step.
 *
 * Stops as soon as the session advances, so a test never accidentally feeds surplus frames into the
 * detector for the *next* challenge.
 */
internal fun LivenessSession.perform(
    challenge: Challenge,
    from: Long,
    stepMs: Long = 50L,
    trackingId: Int? = PRIMARY_FACE,
    maxFrames: Int = 12,
): Long {
    val frames = framesFor(challenge)
    val indexBefore = currentIndex()
    var now = from
    repeat(maxFrames) { i ->
        process(frames[i % frames.size](now, trackingId))
        now += stepMs
        if (currentIndex() != indexBefore || state.value !is SessionState.ChallengeInProgress) {
            return now
        }
    }
    error("session did not advance past $challenge after $maxFrames frames; state=${state.value}")
}
