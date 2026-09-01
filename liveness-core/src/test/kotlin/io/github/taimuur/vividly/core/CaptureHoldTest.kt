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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capture hold exists so the photo is not taken while the user's head is still turned away.
 * These tests pin both halves of that: it must not succeed in a bad pose, and it must not become a
 * blind delay in which an unverified face could be substituted.
 */
class CaptureHoldTest {

    private fun config(
        vararg challenges: Challenge,
        captureHold: CaptureHold = CaptureHold(minConsecutiveFrames = 1),
        challengeTimeoutMs: Long = 15_000,
    ) = LivenessConfig(
        challenges = challenges.toList(),
        challengeCount = challenges.size,
        randomizeOrder = false,
        minConsecutiveFrames = 1,
        challengeTimeoutMs = challengeTimeoutMs,
        captureHold = captureHold,
    )

    // --- the bug this exists to fix ---------------------------------------------------------

    @Test
    fun `a turn as the last challenge does not capture while the head is still turned`() {
        val session = LivenessSession(config(Challenge.TURN_LEFT))

        session.process(turnedLeftFace(0))

        assertEquals(
            "the turn is complete, but the head is still rotated away from the camera",
            SessionState.AwaitingCapture,
            session.state.value,
        )
    }

    @Test
    fun `capture happens once the user faces the camera again`() {
        val session = LivenessSession(config(Challenge.TURN_LEFT))

        session.process(turnedLeftFace(0))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        session.process(face(50)) // back to neutral

        val state = session.state.value
        assertTrue("expected success but was $state", state is SessionState.Succeeded)
        state as SessionState.Succeeded
        assertEquals(listOf(Challenge.TURN_LEFT), state.completedChallenges)
    }

    @Test
    fun `a partial turn back is still not good enough`() {
        val session = LivenessSession(config(Challenge.TURN_LEFT))

        session.process(turnedLeftFace(0))
        // Default maxYawDegrees is 10; 25 is on the way back but still clearly off-camera.
        session.process(face(50, yaw = 25f))

        assertEquals(SessionState.AwaitingCapture, session.state.value)
    }

    @Test
    fun `turning back the other way is equally unacceptable`() {
        val session = LivenessSession(config(Challenge.TURN_LEFT))

        session.process(turnedLeftFace(0))
        session.process(face(50, yaw = -25f))

        assertEquals(SessionState.AwaitingCapture, session.state.value)
    }

    @Test
    fun `capture waits for the eyes to be open`() {
        val session = LivenessSession(config(Challenge.SMILE))

        session.process(smilingFace(0))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        // Neutral pose, but caught mid-blink.
        session.process(eyesClosedFace(50))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        session.process(eyesOpenFace(100))
        assertTrue(session.state.value is SessionState.Succeeded)
    }

    @Test
    fun `the hold requires consecutive frames`() {
        val session = LivenessSession(
            config(Challenge.TURN_LEFT, captureHold = CaptureHold(minConsecutiveFrames = 3)),
        )

        session.process(turnedLeftFace(0))
        session.process(face(50))
        session.process(face(100))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        // Looking away breaks the run, so it starts over.
        session.process(turnedLeftFace(150))
        session.process(face(200))
        session.process(face(250))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        session.process(face(300))
        assertTrue(session.state.value is SessionState.Succeeded)
    }

    // --- the hold is verified, not a delay --------------------------------------------------

    @Test
    fun `losing the face during the hold resets the whole session`() {
        val session = LivenessSession(config(Challenge.SMILE, Challenge.TURN_LEFT))

        session.process(smilingFace(0))
        session.process(turnedLeftFace(50))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        session.process(noFace(100))
        assertEquals(SessionState.SearchingForFace, session.state.value)

        session.process(face(150))
        assertEquals(
            "progress must restart at the first challenge, not resume the hold",
            SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2),
            session.state.value,
        )
    }

    @Test
    fun `a second face during the hold resets the whole session`() {
        val session = LivenessSession(config(Challenge.SMILE, Challenge.TURN_LEFT))

        session.process(smilingFace(0))
        session.process(turnedLeftFace(50))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        session.process(twoFaces(100))
        assertEquals(SessionState.MultipleFaces, session.state.value)

        session.process(face(150))
        assertEquals(
            SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2),
            session.state.value,
        )
    }

    @Test
    fun `a different face cannot walk into the hold and be photographed`() {
        val session = LivenessSession(config(Challenge.SMILE, Challenge.TURN_LEFT))

        // Face A does all the work.
        session.process(smilingFace(0))
        session.process(turnedLeftFace(50))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        // Face B presents a perfect, camera-facing pose — the easiest possible frame to capture.
        session.process(face(100, trackingId = 99))
        session.process(face(150, trackingId = 99))

        val state = session.state.value
        assertTrue("a substituted face must not be captured, but state was $state", state !is SessionState.Succeeded)
        assertEquals(
            SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2),
            state,
        )
    }

    @Test
    fun `the hold can time out`() {
        val session = LivenessSession(
            config(Challenge.TURN_LEFT, challengeTimeoutMs = 500),
        )

        session.process(turnedLeftFace(0))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        // Never returns to a neutral pose.
        session.process(turnedLeftFace(499))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        session.process(turnedLeftFace(500))
        assertEquals(SessionState.Failed(FailureReason.CHALLENGE_TIMEOUT), session.state.value)
    }

    @Test
    fun `the hold gets its own timeout budget`() {
        val session = LivenessSession(
            config(Challenge.SMILE, Challenge.TURN_LEFT, challengeTimeoutMs = 500),
        )

        session.process(smilingFace(0))
        session.process(turnedLeftFace(400))
        assertEquals(SessionState.AwaitingCapture, session.state.value)

        // 800ms into the session, but only 400ms since the hold began.
        session.process(turnedLeftFace(800))
        assertEquals(SessionState.AwaitingCapture, session.state.value)
    }

    // --- opting out --------------------------------------------------------------------------

    @Test
    fun `disabling the hold restores capture on the final challenge frame`() {
        val session = LivenessSession(
            config(Challenge.TURN_LEFT, captureHold = CaptureHold(enabled = false)),
        )

        session.process(turnedLeftFace(0))

        assertTrue(session.state.value is SessionState.Succeeded)
    }

    @Test
    fun `eye checks can be waived`() {
        val session = LivenessSession(
            config(
                Challenge.SMILE,
                captureHold = CaptureHold(requireEyesOpen = false, minConsecutiveFrames = 1),
            ),
        )

        session.process(smilingFace(0))
        session.process(eyesClosedFace(50))

        assertTrue(session.state.value is SessionState.Succeeded)
    }

    @Test
    fun `a detector that reports no yaw does not stall the hold`() {
        val session = LivenessSession(config(Challenge.SMILE))

        session.process(smilingFace(0))
        // Nothing to judge the pose by; the hold must not fail closed and stall until timeout.
        session.process(face(50, yaw = null))

        assertTrue(session.state.value is SessionState.Succeeded)
    }

    // --- configuration -----------------------------------------------------------------------

    @Test
    fun `rejects a non-positive yaw window`() {
        assertThrows(IllegalArgumentException::class.java) {
            CaptureHold(maxYawDegrees = 0f)
        }
    }

    @Test
    fun `rejects a consecutive frame count below one`() {
        assertThrows(IllegalArgumentException::class.java) {
            CaptureHold(minConsecutiveFrames = 0)
        }
    }

    @Test
    fun `the hold is on by default`() {
        assertTrue(LivenessConfig().captureHold.enabled)
    }
}
