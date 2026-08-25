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

class LivenessConfigTest {

    @Test
    fun `defaults are usable`() {
        val config = LivenessConfig()

        assertEquals(Challenge.entries, config.challenges)
        assertEquals(3, config.challengeCount)
        assertTrue("randomizing the order is the anti-replay default", config.randomizeOrder)
    }

    @Test
    fun `rejects an empty challenge pool`() {
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(challenges = emptyList(), challengeCount = 1)
        }
    }

    @Test
    fun `rejects duplicate challenges`() {
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(
                challenges = listOf(Challenge.SMILE, Challenge.SMILE),
                challengeCount = 2,
            )
        }
    }

    @Test
    fun `rejects asking for more challenges than the pool holds`() {
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(challenges = listOf(Challenge.SMILE), challengeCount = 2)
        }
    }

    @Test
    fun `rejects a challenge count below one`() {
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(challengeCount = 0)
        }
    }

    @Test
    fun `rejects non-positive timeouts`() {
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(challengeTimeoutMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(sessionTimeoutMs = -1)
        }
    }

    @Test
    fun `rejects a consecutive frame count below one`() {
        assertThrows(IllegalArgumentException::class.java) {
            LivenessConfig(minConsecutiveFrames = 0)
        }
    }

    @Test
    fun `rejects probabilities outside zero to one`() {
        assertThrows(IllegalArgumentException::class.java) {
            DetectionThresholds(smile = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetectionThresholds(eyeClosed = -0.1f)
        }
    }

    @Test
    fun `rejects an inverted hysteresis band`() {
        assertThrows(IllegalArgumentException::class.java) {
            DetectionThresholds(eyeClosed = 0.8f, eyeOpen = 0.2f)
        }
    }

    @Test
    fun `rejects a non-positive turn angle`() {
        assertThrows(IllegalArgumentException::class.java) {
            DetectionThresholds(turnAngleDegrees = 0f)
        }
    }
}
