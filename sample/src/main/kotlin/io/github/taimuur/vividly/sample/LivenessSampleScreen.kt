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

package io.github.taimuur.vividly.sample

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.taimuur.vividly.camera.LivenessCaptureResult
import io.github.taimuur.vividly.compose.VividlyLiveness
import io.github.taimuur.vividly.core.FailureReason
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drives the sample through three screens: ask for the camera, run the check, show the outcome.
 */
@Composable
internal fun LivenessSampleScreen() {
    val context = LocalContext.current

    var permissionGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    var result by remember { mutableStateOf<LivenessCaptureResult?>(null) }

    // Bumping this rebuilds VividlyLiveness from scratch on retry, which draws a fresh challenge
    // order rather than replaying the one the user just failed.
    var attempt by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val currentResult = result
    when {
        !permissionGranted -> CameraPermissionPrompt(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )

        currentResult == null -> key(attempt) {
            VividlyLiveness(
                modifier = Modifier.fillMaxSize(),
                onResult = { result = it },
            )
        }

        else -> ResultScreen(
            result = currentResult,
            onRetry = {
                result = null
                attempt++
            },
        )
    }
}

@Composable
private fun CameraPermissionPrompt(onRequest: () -> Unit) {
    CenteredColumn {
        Text(
            text = "Vividly needs the camera to check that a real person is in front of it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Grant camera access") }
    }
}

@Composable
private fun ResultScreen(result: LivenessCaptureResult, onRetry: () -> Unit) {
    CenteredColumn {
        when (result) {
            is LivenessCaptureResult.Success -> {
                Text("Verified", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Completed: " + result.completedChallenges.joinToString { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                CapturedPhoto(result.photoFile)
            }

            is LivenessCaptureResult.Failure -> {
                Text("Not verified", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = result.reason.explain(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            is LivenessCaptureResult.Error -> {
                Text("Something went wrong", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = result.cause.message ?: result.cause::class.java.simpleName,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** Decodes the captured JPEG off the main thread and shows it upright. */
@Composable
private fun CapturedPhoto(file: File) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { loadUprightBitmap(file) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = image,
                contentDescription = "The photo captured when verification succeeded",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

private fun FailureReason.explain(): String = when (this) {
    FailureReason.SESSION_TIMEOUT -> "The session ran out of time."
    FailureReason.CHALLENGE_TIMEOUT -> "One of the actions took too long."
    FailureReason.CANCELLED -> "The check was cancelled."
}
