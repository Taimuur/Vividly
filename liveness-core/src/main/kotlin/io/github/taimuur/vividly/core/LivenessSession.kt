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

import io.github.taimuur.vividly.core.detector.ChallengeDetector
import io.github.taimuur.vividly.core.detector.detectorFor
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The liveness state machine: feed it [FaceFrame]s, observe [state].
 *
 * The session draws a plan of [LivenessConfig.challengeCount] challenges, asks for them one at a
 * time, and reaches [SessionState.Succeeded] once the user has performed all of them. It has no
 * Android, camera or clock dependencies of its own — time comes from [FaceFrame.timestampMs] and
 * randomness from the injected [random] — so its entire behaviour is reproducible in a unit test.
 *
 * Progress is deliberately fragile, because that is what makes it evidence:
 * * losing the face, or seeing more than one, resets the session back to the first challenge;
 * * a change of [FaceFrame.trackingId] resets it too, so one person cannot start a session and
 *   another finish it;
 * * [SessionState.Succeeded] and [SessionState.Failed] are absorbing. Once reached, [process] does
 *   nothing at all until [reset] is called, so no late frame can promote a failed session or
 *   demote a successful one.
 *
 * Instances are safe to use from multiple threads: a camera thread can call [process] while the UI
 * thread calls [cancel].
 *
 * @param config Session configuration, see [LivenessConfig].
 * @param random Source of randomness for the challenge order. Inject a seeded [Random] to make
 *   tests deterministic; leave it at [Random.Default] in production, where an unpredictable order
 *   is the point.
 */
public class LivenessSession @JvmOverloads constructor(
    private val config: LivenessConfig = LivenessConfig(),
    private val random: Random = Random.Default,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)

    /** The current state, and a stream of every state the session passes through. */
    public val state: StateFlow<SessionState> = _state.asStateFlow()

    private var plan: List<Challenge> = drawPlan()
    private var detectors: List<ChallengeDetector> = buildDetectors(plan)

    private var challengeIndex = 0
    private var sessionStartMs: Long? = null
    private var challengeStartMs: Long? = null
    private var trackingId: Int? = null

    /**
     * The challenges this session will ask for, in order.
     *
     * Available before the first frame arrives so a host can pre-render the steps, and replaced by
     * a freshly drawn plan on every [reset].
     */
    public val challengePlan: List<Challenge>
        @Synchronized get() = plan

    /**
     * Feeds one observation into the state machine, possibly advancing [state].
     *
     * Safe to call from any thread. Does nothing once the session has reached a terminal state.
     */
    @Synchronized
    public fun process(frame: FaceFrame) {
        if (_state.value.isTerminal()) return

        val startedAt = sessionStartMs ?: frame.timestampMs.also { sessionStartMs = it }
        if (frame.timestampMs - startedAt >= config.sessionTimeoutMs) {
            fail(FailureReason.SESSION_TIMEOUT)
            return
        }

        // Anything other than exactly one face means we cannot attribute the next action to the
        // person we are verifying, so progress is thrown away rather than merely paused.
        if (frame.faceCount <= 0) {
            resetProgress()
            _state.value = SessionState.SearchingForFace
            return
        }
        if (frame.faceCount > 1) {
            resetProgress()
            _state.value = SessionState.MultipleFaces
            return
        }

        adoptTrackingId(frame.trackingId)

        val challengeStartedAt = challengeStartMs
        if (challengeStartedAt == null) {
            challengeStartMs = frame.timestampMs
        } else if (frame.timestampMs - challengeStartedAt >= config.challengeTimeoutMs) {
            fail(FailureReason.CHALLENGE_TIMEOUT)
            return
        }

        _state.value = currentChallengeState()

        if (detectors[challengeIndex].onFrame(frame)) {
            advance(frame, startedAt)
        }
    }

    /** Ends the session with [FailureReason.CANCELLED], unless it already finished. */
    @Synchronized
    public fun cancel() {
        if (_state.value.isTerminal()) return
        fail(FailureReason.CANCELLED)
    }

    /**
     * Returns the session to [SessionState.Idle] and draws a **new** challenge plan.
     *
     * The new draw is what makes a retry meaningfully different from the attempt before it, rather
     * than a second chance at the same script.
     */
    @Synchronized
    public fun reset() {
        plan = drawPlan()
        detectors = buildDetectors(plan)
        challengeIndex = 0
        sessionStartMs = null
        challengeStartMs = null
        trackingId = null
        _state.value = SessionState.Idle
    }

    private fun advance(frame: FaceFrame, startedAt: Long) {
        challengeIndex++
        if (challengeIndex >= plan.size) {
            _state.value = SessionState.Succeeded(
                completedChallenges = plan,
                trackingId = trackingId,
                durationMs = frame.timestampMs - startedAt,
            )
            return
        }
        // Every challenge gets the full per-challenge budget, timed from the moment it is asked.
        challengeStartMs = frame.timestampMs
        detectors[challengeIndex].reset()
        _state.value = currentChallengeState()
    }

    /**
     * Notices when the tracked face is replaced by a different one.
     *
     * Frames without a tracking id are accepted as-is: an absent id is "the detector cannot tell",
     * not "a new person", and treating it as a change would reset progress on every frame when
     * tracking is unavailable.
     */
    private fun adoptTrackingId(incoming: Int?) {
        if (incoming == null) return
        val known = trackingId
        if (known != null && known != incoming) {
            resetProgress()
        }
        trackingId = incoming
    }

    private fun resetProgress() {
        challengeIndex = 0
        challengeStartMs = null
        trackingId = null
        detectors.forEach { it.reset() }
    }

    private fun currentChallengeState() = SessionState.ChallengeInProgress(
        challenge = plan[challengeIndex],
        index = challengeIndex,
        total = plan.size,
    )

    private fun fail(reason: FailureReason) {
        _state.value = SessionState.Failed(reason)
    }

    private fun drawPlan(): List<Challenge> {
        val pool = if (config.randomizeOrder) config.challenges.shuffled(random) else config.challenges
        return pool.take(config.challengeCount)
    }

    private fun buildDetectors(plan: List<Challenge>): List<ChallengeDetector> = plan.map {
        detectorFor(it, config.thresholds, config.minConsecutiveFrames)
    }

    private fun SessionState.isTerminal(): Boolean =
        this is SessionState.Succeeded || this is SessionState.Failed
}
