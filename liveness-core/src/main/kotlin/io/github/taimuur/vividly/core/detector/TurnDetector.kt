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
 * Passes once the head has been rotated past [DetectionThresholds.turnAngleDegrees] in the
 * requested direction for [minConsecutiveFrames] frames in a row.
 *
 * @param towardUserLeft `true` for [Challenge.TURN_LEFT][io.github.taimuur.vividly.core.Challenge.TURN_LEFT],
 *   which requires a yaw of `+turnAngleDegrees` or more; `false` for `TURN_RIGHT`, which requires
 *   `-turnAngleDegrees` or less. See [FaceFrame.headYawDegrees] for why positive means the user's
 *   own left.
 */
internal class TurnDetector(
    private val thresholds: DetectionThresholds,
    private val minConsecutiveFrames: Int,
    private val towardUserLeft: Boolean,
) : ChallengeDetector {

    private var consecutiveFrames = 0

    override fun onFrame(frame: FaceFrame): Boolean {
        val yaw = frame.headYawDegrees
        if (yaw == null || !isTurnedFarEnough(yaw)) {
            consecutiveFrames = 0
            return false
        }
        consecutiveFrames++
        return consecutiveFrames >= minConsecutiveFrames
    }

    private fun isTurnedFarEnough(yaw: Float): Boolean = if (towardUserLeft) {
        yaw >= thresholds.turnAngleDegrees
    } else {
        yaw <= -thresholds.turnAngleDegrees
    }

    override fun reset() {
        consecutiveFrames = 0
    }
}
