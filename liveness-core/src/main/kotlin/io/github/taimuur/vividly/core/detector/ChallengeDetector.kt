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

package io.github.taimuur.vividly.core.detector

import io.github.taimuur.vividly.core.Challenge
import io.github.taimuur.vividly.core.DetectionThresholds
import io.github.taimuur.vividly.core.FaceFrame

/**
 * Decides whether a single [Challenge] has been satisfied, given a stream of [FaceFrame]s.
 *
 * A detector is stateful across frames and is always fed frames belonging to one continuous
 * observation of one face: [LivenessSession][io.github.taimuur.vividly.core.LivenessSession]
 * calls [reset] whenever that continuity breaks, for instance when the face is lost or a different
 * person appears.
 */
internal interface ChallengeDetector {

    /**
     * Feeds one frame in and reports whether the challenge is now complete.
     *
     * Returning `true` is a one-shot event: the session advances immediately and will not feed this
     * detector again without calling [reset] first.
     */
    fun onFrame(frame: FaceFrame): Boolean

    /** Drops all accumulated evidence, returning the detector to its initial state. */
    fun reset()
}

/** Builds the detector that implements [challenge]. */
internal fun detectorFor(
    challenge: Challenge,
    thresholds: DetectionThresholds,
    minConsecutiveFrames: Int,
): ChallengeDetector = when (challenge) {
    Challenge.SMILE -> SmileDetector(thresholds, minConsecutiveFrames)
    Challenge.BLINK -> BlinkDetector(thresholds)
    Challenge.TURN_LEFT -> TurnDetector(thresholds, minConsecutiveFrames, towardUserLeft = true)
    Challenge.TURN_RIGHT -> TurnDetector(thresholds, minConsecutiveFrames, towardUserLeft = false)
}
