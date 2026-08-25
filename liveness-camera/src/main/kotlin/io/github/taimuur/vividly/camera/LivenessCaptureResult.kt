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

package io.github.taimuur.vividly.camera

import io.github.taimuur.vividly.core.Challenge
import io.github.taimuur.vividly.core.FailureReason
import java.io.File

/**
 * The outcome of a run of [LivenessCameraController]. Exactly one result is delivered per run.
 */
public sealed interface LivenessCaptureResult {

    /**
     * The user completed every challenge and a photo was captured automatically.
     *
     * @property photoFile The captured JPEG, written inside the controller's output directory. The
     *   caller owns this file, including deleting it once it has been uploaded or discarded.
     * @property completedChallenges The challenges the user performed, in the order asked.
     */
    public data class Success(
        val photoFile: File,
        val completedChallenges: List<Challenge>,
    ) : LivenessCaptureResult

    /** The session ended without completing: a timeout, or cancellation. */
    public data class Failure(val reason: FailureReason) : LivenessCaptureResult

    /**
     * Something went wrong outside the state machine: the camera could not be bound, or the photo
     * could not be captured or written.
     *
     * These are reported rather than logged, because "the camera silently never started" and "the
     * user failed the check" need very different handling from a host application.
     */
    public data class Error(val cause: Throwable) : LivenessCaptureResult
}
