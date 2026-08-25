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
 * One frame of face observations, produced by whatever camera and face-detection layer the host
 * application uses.
 *
 * This type is deliberately free of any Android or ML Kit dependency: it is the only thing
 * [LivenessSession] consumes, which is what makes the whole state machine unit-testable and lets
 * callers plug in a detector of their own.
 *
 * Every probability field is `null` when the underlying detector did not report a value, for
 * example when face classification is switched off. Detectors treat `null` as "cannot confirm"
 * rather than as a negative observation.
 *
 * @property timestampMs A monotonic timestamp in milliseconds, supplied by the producer. Session
 *   and per-challenge timeouts are measured against this value and never against the wall clock,
 *   which keeps sessions fully deterministic under test. On Android, use
 *   `SystemClock.elapsedRealtime()`.
 * @property faceCount How many faces the detector found in this frame. A session only makes
 *   progress while exactly one face is present.
 * @property trackingId Stable identifier of the primary face across frames, when the detector
 *   supports tracking. Used to notice that a *different* person stepped in front of the camera
 *   mid-session. `null` when tracking is unavailable.
 * @property smilingProbability Probability in `0f..1f` that the primary face is smiling.
 * @property leftEyeOpenProbability Probability in `0f..1f` that the primary left eye is open.
 * @property rightEyeOpenProbability Probability in `0f..1f` that the primary right eye is open.
 * @property headYawDegrees Rotation of the primary face around the vertical axis, in degrees.
 *
 *   **Sign convention: positive means the user turned their head to their own left**
 *   ([Challenge.TURN_LEFT]); negative means their own right ([Challenge.TURN_RIGHT]).
 *
 *   This also matches what the user sees, because preview surfaces mirror the front camera: a user
 *   turning to their own left appears to move toward the left edge of the preview. The *analysis*
 *   frames a camera pipeline receives are normally **not** mirrored, and ML Kit documents
 *   `headEulerAngleY` as positive when the face turns toward the right side of the processed
 *   image, which is the same physical direction. A raw ML Kit yaw can therefore be passed through
 *   unchanged, and `vividly-camera` does exactly that.
 */
public data class FaceFrame(
    val timestampMs: Long,
    val faceCount: Int,
    val trackingId: Int? = null,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headYawDegrees: Float? = null,
)
