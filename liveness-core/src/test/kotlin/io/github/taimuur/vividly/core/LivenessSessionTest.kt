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

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessSessionTest {

    private fun fixedOrderConfig(
        vararg challenges: Challenge,
        challengeTimeoutMs: Long = 15_000,
        sessionTimeoutMs: Long = 60_000,
        minConsecutiveFrames: Int = 1,
    ) = LivenessConfig(
        challenges = challenges.toList(),
        challengeCount = challenges.size,
        randomizeOrder = false,
        challengeTimeoutMs = challengeTimeoutMs,
        sessionTimeoutMs = sessionTimeoutMs,
        minConsecutiveFrames = minConsecutiveFrames,
    )

    // --- happy path -------------------------------------------------------------------------

    @Test
    fun `completes every challenge in the plan`() {
        val session = LivenessSession(LivenessConfig(), Random(1234))
        val plan = session.challengePlan
        assertEquals(3, plan.size)

        var now = 0L
        session.process(face(now))
        now += 50

        repeat(plan.size) {
            now = session.perform(session.currentChallenge(), now)
        }

        val state = session.state.value
        assertTrue("expected success but was $state", state is SessionState.Succeeded)
        state as SessionState.Succeeded
        assertEquals(plan, state.completedChallenges)
        assertEquals(PRIMARY_FACE, state.trackingId)
        assertTrue("duration should be positive, was ${state.durationMs}", state.durationMs > 0)
    }

    @Test
    fun `starts idle and asks for the first challenge once a face appears`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE, Challenge.BLINK))
        assertEquals(SessionState.Idle, session.state.value)

        session.process(face(0))

        assertEquals(SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2), session.state.value)
    }

    // --- blink ------------------------------------------------------------------------------

    @Test
    fun `blink is not satisfied by closed eyes alone`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.BLINK))

        // A photograph of someone with their eyes shut, held up for many frames.
        repeat(10) { i -> session.process(eyesClosedFace(i * 50L)) }

        assertEquals(SessionState.ChallengeInProgress(Challenge.BLINK, 0, 1), session.state.value)
    }

    @Test
    fun `blink requires the eyes to reopen after closing`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.BLINK))

        session.process(eyesOpenFace(0))
        session.process(eyesClosedFace(50))

        assertEquals(
            "closing the eyes must not be enough on its own",
            SessionState.ChallengeInProgress(Challenge.BLINK, 0, 1),
            session.state.value,
        )

        session.process(eyesOpenFace(100))

        assertTrue(session.state.value is SessionState.Succeeded)
    }

    @Test
    fun `blink ignores eye probabilities inside the hysteresis band`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.BLINK))

        session.process(eyesOpenFace(0))
        // Detector jitter around the middle: neither open nor closed, so nothing may be inferred.
        repeat(6) { i ->
            session.process(face(50 + i * 50L, leftEyeOpen = 0.5f, rightEyeOpen = 0.5f))
        }
        session.process(eyesOpenFace(400))

        assertEquals(SessionState.ChallengeInProgress(Challenge.BLINK, 0, 1), session.state.value)
    }

    @Test
    fun `blink requires both eyes to close`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.BLINK))

        session.process(eyesOpenFace(0))
        // A wink: one eye shut, the other wide open.
        session.process(face(50, leftEyeOpen = 0.05f, rightEyeOpen = 0.95f))
        session.process(eyesOpenFace(100))

        assertEquals(SessionState.ChallengeInProgress(Challenge.BLINK, 0, 1), session.state.value)
    }

    // --- smile and turn stability -----------------------------------------------------------

    @Test
    fun `smile requires consecutive frames`() {
        val session = LivenessSession(
            fixedOrderConfig(Challenge.SMILE, minConsecutiveFrames = 3),
        )

        session.process(smilingFace(0))
        session.process(smilingFace(50))
        assertEquals(
            "two frames must not be enough when three are required",
            SessionState.ChallengeInProgress(Challenge.SMILE, 0, 1),
            session.state.value,
        )

        // A neutral frame breaks the run, so the count starts over.
        session.process(face(100))
        session.process(smilingFace(150))
        session.process(smilingFace(200))
        assertEquals(
            SessionState.ChallengeInProgress(Challenge.SMILE, 0, 1),
            session.state.value,
        )

        session.process(smilingFace(250))
        assertTrue(session.state.value is SessionState.Succeeded)
    }

    @Test
    fun `turning the wrong way does not satisfy a turn challenge`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.TURN_LEFT))

        repeat(5) { i -> session.process(turnedRightFace(i * 50L)) }
        assertEquals(
            SessionState.ChallengeInProgress(Challenge.TURN_LEFT, 0, 1),
            session.state.value,
        )

        session.process(turnedLeftFace(500))
        assertTrue(session.state.value is SessionState.Succeeded)
    }

    @Test
    fun `turn requires consecutive frames`() {
        val session = LivenessSession(
            fixedOrderConfig(Challenge.TURN_RIGHT, minConsecutiveFrames = 3),
        )

        session.process(turnedRightFace(0))
        session.process(turnedRightFace(50))
        session.process(face(100)) // looked back at the camera, breaking the run
        session.process(turnedRightFace(150))
        session.process(turnedRightFace(200))
        assertEquals(
            SessionState.ChallengeInProgress(Challenge.TURN_RIGHT, 0, 1),
            session.state.value,
        )

        session.process(turnedRightFace(250))
        assertTrue(session.state.value is SessionState.Succeeded)
    }

    // --- progress resets --------------------------------------------------------------------

    @Test
    fun `losing the face resets progress to the first challenge`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE, Challenge.BLINK))

        session.process(face(0))
        session.process(smilingFace(50))
        assertEquals(1, session.currentIndex())

        session.process(noFace(100))
        assertEquals(SessionState.SearchingForFace, session.state.value)

        session.process(face(150))
        assertEquals(SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2), session.state.value)
    }

    @Test
    fun `a second face resets progress and reports multiple faces`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE, Challenge.BLINK))

        session.process(face(0))
        session.process(smilingFace(50))
        assertEquals(1, session.currentIndex())

        session.process(twoFaces(100))
        assertEquals(SessionState.MultipleFaces, session.state.value)

        session.process(face(150))
        assertEquals(SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2), session.state.value)
    }

    @Test
    fun `a different tracked face resets progress`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE, Challenge.BLINK))

        session.process(face(0))
        session.process(smilingFace(50))
        assertEquals(1, session.currentIndex())

        session.process(face(100, trackingId = 99))

        assertEquals(SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2), session.state.value)
    }

    @Test
    fun `a substituted face cannot inherit the first face's progress`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE, Challenge.BLINK))

        // Face A completes the smile.
        session.process(face(0))
        session.process(smilingFace(50))

        // Face B steps in and blinks, hoping to finish a session it never started.
        session.process(eyesOpenFace(100, trackingId = 99))
        session.process(eyesClosedFace(150, trackingId = 99))
        session.process(eyesOpenFace(200, trackingId = 99))

        val state = session.state.value
        assertTrue("a swapped face must not succeed, but state was $state", state !is SessionState.Succeeded)
        assertEquals(SessionState.ChallengeInProgress(Challenge.SMILE, 0, 2), state)
    }

    @Test
    fun `frames without a tracking id do not reset progress`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE, Challenge.BLINK))

        session.process(face(0, trackingId = null))
        session.process(smilingFace(50, trackingId = null))
        assertEquals(1, session.currentIndex())

        session.process(face(100, trackingId = null))
        assertEquals(1, session.currentIndex())
    }

    // --- timeouts ---------------------------------------------------------------------------

    @Test
    fun `session times out`() {
        val session = LivenessSession(
            fixedOrderConfig(Challenge.SMILE, Challenge.BLINK, sessionTimeoutMs = 1_000),
        )

        session.process(face(0))
        session.process(face(999))
        assertTrue(session.state.value is SessionState.ChallengeInProgress)

        session.process(face(1_000))
        assertEquals(SessionState.Failed(FailureReason.SESSION_TIMEOUT), session.state.value)
    }

    @Test
    fun `the session clock keeps running while no face is visible`() {
        val session = LivenessSession(
            fixedOrderConfig(Challenge.SMILE, sessionTimeoutMs = 1_000),
        )

        session.process(face(0))
        session.process(noFace(500))
        session.process(noFace(1_000))

        assertEquals(SessionState.Failed(FailureReason.SESSION_TIMEOUT), session.state.value)
    }

    @Test
    fun `a single challenge times out`() {
        val session = LivenessSession(
            fixedOrderConfig(Challenge.SMILE, Challenge.BLINK, challengeTimeoutMs = 500),
        )

        session.process(face(0))
        session.process(face(499))
        assertTrue(session.state.value is SessionState.ChallengeInProgress)

        session.process(face(500))
        assertEquals(SessionState.Failed(FailureReason.CHALLENGE_TIMEOUT), session.state.value)
    }

    @Test
    fun `the challenge clock restarts on each advance`() {
        val session = LivenessSession(
            fixedOrderConfig(Challenge.SMILE, Challenge.BLINK, challengeTimeoutMs = 500),
        )

        session.process(face(0))
        session.process(smilingFace(400))
        assertEquals(1, session.currentIndex())

        // 800ms since the session started, but only 400ms since the blink was asked for.
        session.process(eyesOpenFace(800))

        assertEquals(SessionState.ChallengeInProgress(Challenge.BLINK, 1, 2), session.state.value)
    }

    // --- cancellation and terminal states ---------------------------------------------------

    @Test
    fun `cancel fails the session`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE))
        session.process(face(0))

        session.cancel()

        assertEquals(SessionState.Failed(FailureReason.CANCELLED), session.state.value)
    }

    @Test
    fun `cancel after success is ignored`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE))
        session.process(smilingFace(0))
        val succeeded = session.state.value
        assertTrue(succeeded is SessionState.Succeeded)

        session.cancel()

        assertEquals(succeeded, session.state.value)
    }

    @Test
    fun `success is absorbing`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE))
        session.process(smilingFace(0))
        val succeeded = session.state.value as SessionState.Succeeded

        session.process(noFace(100))
        session.process(twoFaces(200))
        session.process(face(999_999))

        assertEquals(succeeded, session.state.value)
    }

    @Test
    fun `failure is absorbing`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE))
        session.process(face(0))
        session.cancel()

        session.process(smilingFace(50))
        session.process(smilingFace(100))

        assertEquals(SessionState.Failed(FailureReason.CANCELLED), session.state.value)
    }

    @Test
    fun `reset returns to idle and allows a fresh run`() {
        val session = LivenessSession(fixedOrderConfig(Challenge.SMILE))
        session.process(face(0))
        session.cancel()

        session.reset()
        assertEquals(SessionState.Idle, session.state.value)

        session.process(smilingFace(0))
        assertTrue(session.state.value is SessionState.Succeeded)
    }

    // --- challenge plan ---------------------------------------------------------------------

    @Test
    fun `the plan is deterministic for a seeded random`() {
        val config = LivenessConfig()

        val first = LivenessSession(config, Random(1234)).challengePlan
        val second = LivenessSession(config, Random(1234)).challengePlan

        assertEquals(first, second)
    }

    @Test
    fun `reset draws the next plan deterministically for a seeded random`() {
        val config = LivenessConfig()

        val session = LivenessSession(config, Random(99))
        session.reset()

        val reference = LivenessSession(config, Random(99))
        reference.reset()

        assertEquals(reference.challengePlan, session.challengePlan)
    }

    @Test
    fun `randomizing produces more than one order across seeds`() {
        val config = LivenessConfig()

        val plans = (0L until 50L)
            .map { seed -> LivenessSession(config, Random(seed)).challengePlan }
            .toSet()

        assertTrue("expected several distinct orders, got $plans", plans.size > 1)
    }

    @Test
    fun `a fresh plan is drawn on reset`() {
        val config = LivenessConfig()
        val session = LivenessSession(config, Random(7))

        // Across many resets the same session must not keep asking for one fixed script.
        val plans = buildSet {
            add(session.challengePlan)
            repeat(30) {
                session.reset()
                add(session.challengePlan)
            }
        }

        assertTrue("expected the plan to vary between sessions, got $plans", plans.size > 1)
    }

    @Test
    fun `order is preserved when randomization is disabled`() {
        val config = LivenessConfig(
            challenges = listOf(
                Challenge.SMILE,
                Challenge.BLINK,
                Challenge.TURN_LEFT,
                Challenge.TURN_RIGHT,
            ),
            challengeCount = 3,
            randomizeOrder = false,
        )

        val plan = LivenessSession(config).challengePlan

        assertEquals(listOf(Challenge.SMILE, Challenge.BLINK, Challenge.TURN_LEFT), plan)
    }

    @Test
    fun `the plan holds exactly the requested number of challenges`() {
        val config = LivenessConfig(challengeCount = 2)

        val plan = LivenessSession(config, Random(3)).challengePlan

        assertEquals(2, plan.size)
        assertEquals(plan.distinct(), plan)
    }
}
