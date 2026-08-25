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

import io.github.taimuur.vividly.core.DetectionThresholds
import io.github.taimuur.vividly.core.FaceFrame

/**
 * Passes only after observing a complete blink: eyes open, then closed, then open again.
 *
 * Demanding the full transition is a security property, not a nicety. Accepting "eyes are closed
 * right now" would let a single photograph of someone with their eyes shut pass the challenge; a
 * blink is a movement, so it can only be confirmed by seeing the eyes come back open.
 *
 * Both eyes must agree, and the open and closed decisions use separate thresholds
 * ([DetectionThresholds.eyeOpen] and [DetectionThresholds.eyeClosed]). Probabilities that fall
 * between the two are ambiguous and leave the detector in whatever state it was already in, so a
 * detector jittering around a single cut-off cannot manufacture a blink.
 */
internal class BlinkDetector(
    private val thresholds: DetectionThresholds,
) : ChallengeDetector {

    private enum class Phase {
        /** Nothing trustworthy seen yet; waiting to confirm the eyes are open to begin with. */
        AWAITING_OPEN,

        /** Eyes confirmed open. Waiting for them to close. */
        OPEN,

        /** Eyes confirmed closed after having been open. Waiting for them to reopen. */
        CLOSED,
    }

    private var phase = Phase.AWAITING_OPEN

    override fun onFrame(frame: FaceFrame): Boolean {
        val left = frame.leftEyeOpenProbability
        val right = frame.rightEyeOpenProbability
        if (left == null || right == null) return false

        val eyesOpen = left > thresholds.eyeOpen && right > thresholds.eyeOpen
        val eyesClosed = left < thresholds.eyeClosed && right < thresholds.eyeClosed

        when (phase) {
            Phase.AWAITING_OPEN -> if (eyesOpen) phase = Phase.OPEN
            Phase.OPEN -> if (eyesClosed) phase = Phase.CLOSED
            Phase.CLOSED -> if (eyesOpen) {
                phase = Phase.AWAITING_OPEN
                return true
            }
        }
        return false
    }

    override fun reset() {
        phase = Phase.AWAITING_OPEN
    }
}
