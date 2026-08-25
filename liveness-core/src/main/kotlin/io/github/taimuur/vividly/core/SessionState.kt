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

/** Why a [LivenessSession] ended without the user completing every challenge. */
public enum class FailureReason {
    /** The total [LivenessConfig.sessionTimeoutMs] budget ran out. */
    SESSION_TIMEOUT,

    /** The user spent longer than [LivenessConfig.challengeTimeoutMs] on a single challenge. */
    CHALLENGE_TIMEOUT,

    /** [LivenessSession.cancel] was called, typically because the user backed out. */
    CANCELLED,
}

/** Everything an observer needs in order to render a liveness session. */
public sealed interface SessionState {

    /** Nothing has been processed yet: a freshly constructed or [LivenessSession.reset] session. */
    public data object Idle : SessionState

    /** No face is currently visible; the session is waiting for one to appear. */
    public data object SearchingForFace : SessionState

    /**
     * More than one face is visible. Progress is reset and held until exactly one face remains, so
     * that a bystander cannot complete challenges on the user's behalf.
     */
    public data object MultipleFaces : SessionState

    /**
     * The user is being asked to perform [challenge].
     *
     * @property index Zero-based position of this challenge within the session plan.
     * @property total How many challenges this session asks for in total.
     */
    public data class ChallengeInProgress(
        val challenge: Challenge,
        val index: Int,
        val total: Int,
    ) : SessionState

    /**
     * Every challenge was completed. Terminal.
     *
     * @property completedChallenges The plan the user completed, in the order it was asked.
     * @property trackingId Identifier of the face that completed the session, when the detector
     *   supports tracking. Callers that capture an image can use it to assert that the captured
     *   face is the one that was verified.
     * @property durationMs How long the session took, derived from [FaceFrame.timestampMs].
     */
    public data class Succeeded(
        val completedChallenges: List<Challenge>,
        val trackingId: Int?,
        val durationMs: Long,
    ) : SessionState

    /** The session ended early. Terminal. */
    public data class Failed(val reason: FailureReason) : SessionState
}
