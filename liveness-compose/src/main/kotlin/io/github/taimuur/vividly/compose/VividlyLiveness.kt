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

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.taimuur.vividly.camera.LivenessCameraController
import io.github.taimuur.vividly.camera.LivenessCaptureResult
import io.github.taimuur.vividly.core.LivenessConfig
import io.github.taimuur.vividly.core.SessionState

/**
 * A ready-made liveness screen: front-camera preview, the challenge state machine, and an overlay
 * telling the user what to do.
 *
 * ```
 * VividlyLiveness(
 *     modifier = Modifier.fillMaxSize(),
 *     onResult = { result ->
 *         when (result) {
 *             is LivenessCaptureResult.Success -> upload(result.photoFile)
 *             is LivenessCaptureResult.Failure -> showRetry(result.reason)
 *             is LivenessCaptureResult.Error -> showError(result.cause)
 *         }
 *     },
 * )
 * ```
 *
 * **The host is responsible for the camera permission.** This composable assumes `CAMERA` has
 * already been granted and shows no permission UI of its own, because permission prompts belong to
 * the host's own onboarding, copy and design. Without the grant, [onResult] reports a
 * [LivenessCaptureResult.Error].
 *
 * To retry after a failure, recompose this with a different `key(...)`, which builds a fresh
 * session with a newly drawn challenge order.
 *
 * @param modifier Applied to the root container holding the preview and the overlay.
 * @param config Session configuration, see [LivenessConfig].
 * @param onResult Called once when the check finishes, on the main thread.
 * @param prompts Maps a [SessionState] to the instruction shown to the user. Override this to
 *   localise; see [VividlyDefaults.promptFor].
 * @param overlay Everything drawn on top of the preview. Override to replace the stock visuals
 *   entirely; the default composes [VividlyDefaults.Overlay] against [prompts].
 */
@Composable
public fun VividlyLiveness(
    modifier: Modifier = Modifier,
    config: LivenessConfig = LivenessConfig(),
    onResult: (LivenessCaptureResult) -> Unit,
    prompts: (SessionState) -> String = VividlyDefaults::promptFor,
    overlay: @Composable BoxScope.(SessionState) -> Unit = VividlyDefaults.defaultOverlay(prompts),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keeps a recomposition from binding the camera to a stale callback.
    val currentOnResult by rememberUpdatedState(onResult)

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val controller = remember(context, config) {
        LivenessCameraController(
            context = context,
            config = config,
            onResult = { result -> currentOnResult(result) },
        )
    }

    val state by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(controller, previewView) {
        controller.bind(lifecycleOwner, previewView.surfaceProvider)
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        overlay(state)
    }
}
