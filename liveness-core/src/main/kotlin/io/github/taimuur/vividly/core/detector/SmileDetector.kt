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
 * Passes once the face has been smiling for [minConsecutiveFrames] frames in a row.
 *
 * Requiring a run of frames rather than a single one matters: classifier output fluctuates from
 * frame to frame, and a lone spike above the threshold is as likely to be noise as a smile.
 */
internal class SmileDetector(
    private val thresholds: DetectionThresholds,
    private val minConsecutiveFrames: Int,
) : ChallengeDetector {

    private var consecutiveFrames = 0

    override fun onFrame(frame: FaceFrame): Boolean {
        val probability = frame.smilingProbability
        if (probability == null || probability < thresholds.smile) {
            consecutiveFrames = 0
            return false
        }
        consecutiveFrames++
        return consecutiveFrames >= minConsecutiveFrames
    }

    override fun reset() {
        consecutiveFrames = 0
    }
}
