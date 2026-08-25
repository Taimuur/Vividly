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
 * A single action the user is asked to perform to prove they are a live person in front of the
 * camera, rather than a photo, a video replay or a mask.
 *
 * Which challenges a session may draw from, how many it asks for and in which order is controlled
 * by [LivenessConfig].
 */
public enum class Challenge {
    /** The user is asked to smile. */
    SMILE,

    /** The user is asked to blink: a full open, then closed, then open eye transition. */
    BLINK,

    /**
     * The user is asked to turn their head to *their own* left.
     *
     * See [FaceFrame.headYawDegrees] for the sign convention this maps to.
     */
    TURN_LEFT,

    /**
     * The user is asked to turn their head to *their own* right.
     *
     * See [FaceFrame.headYawDegrees] for the sign convention this maps to.
     */
    TURN_RIGHT,
}
