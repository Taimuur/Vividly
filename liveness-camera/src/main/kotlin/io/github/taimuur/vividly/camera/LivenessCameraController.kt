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

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.taimuur.vividly.core.Challenge
import io.github.taimuur.vividly.core.FaceFrame
import io.github.taimuur.vividly.core.FailureReason
import io.github.taimuur.vividly.core.LivenessConfig
import io.github.taimuur.vividly.core.LivenessSession
import io.github.taimuur.vividly.core.SessionState
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs a whole liveness check against the front camera: preview, face analysis, the
 * [LivenessSession] state machine, and the photo capture at the end of it.
 *
 * ```
 * val controller = LivenessCameraController(context) { result -> /* handle */ }
 * lifecycleScope.launch { controller.bind(lifecycleOwner, previewView.surfaceProvider) }
 * // later
 * controller.stop()
 * ```
 *
 * **The photo is captured automatically, at the instant the session succeeds.** There is no button
 * between passing the challenges and taking the picture, and that is a security property: if a user
 * had to tap "capture" afterwards, nothing would tie the image that gets captured to the face that
 * actually performed the challenges. Between verification and shutter there is no opportunity to
 * substitute a different face.
 *
 * Observe [state] to drive a UI, and use [onResult] for the single terminal outcome. Every camera
 * and capture failure is delivered as [LivenessCaptureResult.Error] instead of being logged and
 * dropped.
 *
 * @param context Used for the camera provider and to resolve the default output directory. An
 *   application context is fine.
 * @param config Session configuration, see [LivenessConfig].
 * @param outputDirectory Where the captured JPEG is written. Defaults to the app cache directory,
 *   which is appropriate for an image that is about to be uploaded and discarded; pass a directory
 *   of your own if the photo needs to outlive a cache eviction.
 * @param onResult Called exactly once per run, on the main thread.
 */
public class LivenessCameraController(
    private val context: Context,
    private val config: LivenessConfig = LivenessConfig(),
    private val outputDirectory: File = context.cacheDir,
    private val onResult: (LivenessCaptureResult) -> Unit,
) {

    private val session = LivenessSession(config)

    /** The live state of the underlying session, for driving a UI. */
    public val state: StateFlow<SessionState> = session.state

    /** The challenges this run will ask for, in order. Redrawn by [reset]. */
    public val challengePlan: List<Challenge>
        get() = session.challengePlan

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** Guards the single terminal delivery: the first outcome to arrive is the only one. */
    private val resultDelivered = AtomicBoolean(false)

    /**
     * Guards acting on a terminal session state more than once.
     *
     * Analysis keeps delivering frames while a capture is in flight, and every one of them would
     * otherwise see the same [SessionState.Succeeded] and fire another `takePicture`. This flag is
     * set the moment the session first turns terminal, well before the photo has been written.
     */
    private val terminalHandled = AtomicBoolean(false)

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var analyzer: FaceFrameAnalyzer? = null

    /**
     * Binds preview, analysis and capture to [lifecycleOwner] and starts the session.
     *
     * Suspends until the camera is bound. A failure to bind is reported through
     * [LivenessCaptureResult.Error] as well as leaving this function normally, so callers do not
     * have to handle it twice.
     *
     * @param surfaceProvider Where the preview is rendered, typically
     *   `previewView.surfaceProvider`.
     */
    public suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        try {
            val provider = awaitCameraProvider()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(surfaceProvider)
            }

            val frameAnalyzer = FaceFrameAnalyzer(
                onFrame = ::onFaceFrame,
                onError = { deliver(LivenessCaptureResult.Error(it)) },
            )
            // Published before the bind that might throw, so that a failed bind still leaves the
            // ML Kit detector reachable for stop() to close.
            analyzer = frameAnalyzer

            val analysis = ImageAnalysis.Builder()
                // Liveness only cares about what the face is doing *now*; queueing stale frames
                // would make the session lag behind the user.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(analysisExecutor, frameAnalyzer) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // bindToLifecycle must run on the main thread.
            withContext(Dispatchers.Main) {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                    capture,
                )
            }

            cameraProvider = provider
            imageCapture = capture
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            deliver(LivenessCaptureResult.Error(throwable))
        }
    }

    /**
     * Ends the run early with [FailureReason.CANCELLED], for instance when the user navigates back.
     *
     * Delivers a [LivenessCaptureResult.Failure]; see [stop] for silent teardown.
     */
    public fun cancel() {
        terminalHandled.set(true)
        session.cancel()
        deliver(LivenessCaptureResult.Failure(FailureReason.CANCELLED))
    }

    /**
     * Prepares this controller for another attempt: draws a fresh challenge plan and re-arms the
     * result callback. The camera stays bound, so a retry does not have to pay for rebinding.
     */
    public fun reset() {
        session.reset()
        terminalHandled.set(false)
        resultDelivered.set(false)
    }

    /**
     * Releases everything this controller owns: unbinds the camera, cancels the session, closes the
     * ML Kit detector and shuts down the analysis thread.
     *
     * Silent by design — no result is delivered, because tearing down a screen is not an outcome of
     * the check. Use [cancel] when the user actually gave up. The controller cannot be reused
     * afterwards.
     */
    public fun stop() {
        // Set before cancelling, so a frame already in flight on the analysis thread cannot turn
        // teardown into a delivered Failure.
        terminalHandled.set(true)
        session.cancel()
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        analyzer?.close()
        analyzer = null
        analysisExecutor.shutdown()
    }

    private fun onFaceFrame(frame: FaceFrame) {
        if (terminalHandled.get()) return

        session.process(frame)

        when (val current = session.state.value) {
            is SessionState.Succeeded ->
                if (terminalHandled.compareAndSet(false, true)) capturePhoto(current)

            is SessionState.Failed ->
                if (terminalHandled.compareAndSet(false, true)) {
                    deliver(LivenessCaptureResult.Failure(current.reason))
                }

            else -> Unit
        }
    }

    private fun capturePhoto(succeeded: SessionState.Succeeded) {
        val capture = imageCapture
        if (capture == null) {
            deliver(LivenessCaptureResult.Error(IllegalStateException("camera is not bound")))
            return
        }

        // A caller-supplied subdirectory may not exist yet; failing to create it here would surface
        // as an opaque capture error later.
        outputDirectory.mkdirs()
        val photoFile = File(outputDirectory, "vividly-${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    deliver(
                        LivenessCaptureResult.Success(
                            photoFile = photoFile,
                            completedChallenges = succeeded.completedChallenges,
                        ),
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    deliver(LivenessCaptureResult.Error(exception))
                }
            },
        )
    }

    /** Delivers [result] on the main thread, if nothing has been delivered for this run yet. */
    private fun deliver(result: LivenessCaptureResult) {
        if (!resultDelivered.compareAndSet(false, true)) return
        ContextCompat.getMainExecutor(context).execute { onResult(result) }
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (throwable: Throwable) {
                        // ExecutionException tells the caller nothing; the cause is the real story.
                        continuation.resumeWithException(
                            (throwable as? ExecutionException)?.cause ?: throwable,
                        )
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { future.cancel(false) }
        }
}
