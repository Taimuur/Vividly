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

package io.github.taimuur.vividly.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.taimuur.vividly.core.Challenge
import io.github.taimuur.vividly.core.FailureReason
import io.github.taimuur.vividly.core.SessionState

/**
 * The stock prompts and overlay used by [VividlyLiveness] when the caller supplies none.
 *
 * Everything here is a default, not a requirement: pass your own `prompts` for localisation, or
 * your own `overlay` to replace the visuals wholesale.
 */
public object VividlyDefaults {

    /**
     * Plain-English instruction for [state].
     *
     * These are deliberately hard-coded English strings rather than Android string resources: a
     * library that shipped its own resources would drag its own translations, and its own opinions
     * about wording, into every host app. Localise by passing `prompts = { myStrings(it) }` to
     * [VividlyLiveness] and resolving your own resources there.
     */
    public fun promptFor(state: SessionState): String = when (state) {
        SessionState.Idle -> "Starting camera…"
        SessionState.SearchingForFace -> "Position your face in the frame"
        SessionState.MultipleFaces -> "Make sure only you are in the frame"
        is SessionState.ChallengeInProgress -> when (state.challenge) {
            Challenge.SMILE -> "Smile"
            Challenge.BLINK -> "Blink"
            Challenge.TURN_LEFT -> "Slowly turn your head left"
            Challenge.TURN_RIGHT -> "Slowly turn your head right"
        }

        SessionState.AwaitingCapture -> "Look at the camera and hold still"
        is SessionState.Succeeded -> "Done"
        is SessionState.Failed -> when (state.reason) {
            FailureReason.SESSION_TIMEOUT -> "That took too long — please try again"
            FailureReason.CHALLENGE_TIMEOUT -> "We didn't catch that — please try again"
            FailureReason.CANCELLED -> "Cancelled"
        }
    }

    /**
     * The stock overlay: a scrim along the bottom edge, the current [prompt], and a dot per
     * challenge showing how far along the session is.
     *
     * Colours come from the ambient [MaterialTheme], so it inherits the host app's theme rather
     * than imposing a palette of its own.
     */
    @Composable
    public fun Overlay(
        state: SessionState,
        prompt: String,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = prompt,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                )

                val progress = state as? SessionState.ChallengeInProgress
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    StepDots(currentIndex = progress.index, total = progress.total)
                }
            }
        }
    }

    /** One dot per challenge; those already completed are filled in. */
    @Composable
    private fun StepDots(
        currentIndex: Int,
        total: Int,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(total) { index ->
                val done = index < currentIndex
                val current = index == currentIndex
                Box(
                    Modifier
                        .size(if (current) 12.dp else 8.dp)
                        .background(
                            color = when {
                                done -> Color.White
                                current -> Color.White.copy(alpha = 0.9f)
                                else -> Color.White.copy(alpha = 0.35f)
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }

    /** The stock overlay, positioned along the bottom edge of the preview. */
    internal fun defaultOverlay(
        prompts: (SessionState) -> String,
    ): @Composable BoxScope.(SessionState) -> Unit = { state ->
        Overlay(
            state = state,
            prompt = prompts(state),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
