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

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.github.taimuur.vividly.core.FaceFrame
import io.github.taimuur.vividly.core.LivenessSession

/**
 * Turns a CameraX analysis stream into the [FaceFrame]s that a
 * [LivenessSession][io.github.taimuur.vividly.core.LivenessSession] consumes.
 *
 * Use this directly when you want to run your own camera pipeline and your own UI, and only need
 * the ML Kit plumbing; see [LivenessCameraController] for the batteries-included version.
 *
 * ```
 * val analyzer = FaceFrameAnalyzer(
 *     onFrame = session::process,
 *     onError = { Log.w(TAG, "face detection failed", it) },
 * )
 * imageAnalysis.setAnalyzer(executor, analyzer)
 * ```
 *
 * The analyzer owns an ML Kit detector, so call [close] when the analysis use case is torn down.
 *
 * @param onFrame Called once per analysed frame, on the executor the analyzer was set on. Frames
 *   arrive in order but not on the main thread; [LivenessSession.process] is safe to call from here.
 * @param onError Called when ML Kit fails to process a frame. Detection failures are surfaced
 *   rather than logged and swallowed, so a host can decide whether a run of failures means the
 *   session should be abandoned.
 */
public class FaceFrameAnalyzer(
    private val onFrame: (FaceFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // Liveness needs a steady frame rate far more than it needs sub-pixel landmarks.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // Smile and eye-open probabilities are what the challenge detectors run on.
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            // Tracking ids let a session notice that a different person stepped in.
            .enableTracking()
            .build(),
    )

    @Volatile
    private var closed = false

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (closed || mediaImage == null) {
            imageProxy.close()
            return
        }

        // Stamped when the frame was observed rather than when detection finishes, so that session
        // timing reflects the camera, not how long ML Kit happened to take.
        val timestampMs = SystemClock.elapsedRealtime()
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(input)
            .addOnSuccessListener { faces -> onFrame(faces.toFaceFrame(timestampMs)) }
            .addOnFailureListener(onError)
            // Registered last but always runs: the ImageProxy must be released on every path, or
            // the analysis pipeline stalls after a handful of frames.
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Releases the ML Kit detector. The analyzer must not be used afterwards. */
    override fun close() {
        closed = true
        detector.close()
    }
}

/**
 * Reduces a list of detected faces to a single [FaceFrame].
 *
 * The largest bounding box wins: the face nearest the camera is the one being verified, and
 * treating a distant bystander as the subject is exactly the confusion the session's face-count
 * rules exist to prevent.
 */
internal fun List<Face>.toFaceFrame(timestampMs: Long): FaceFrame {
    val primary = maxByOrNull { face ->
        face.boundingBox.width().toLong() * face.boundingBox.height().toLong()
    }
    return FaceFrame(
        timestampMs = timestampMs,
        faceCount = size,
        trackingId = primary?.trackingId,
        smilingProbability = primary?.smilingProbability,
        leftEyeOpenProbability = primary?.leftEyeOpenProbability,
        rightEyeOpenProbability = primary?.rightEyeOpenProbability,
        // ML Kit reports a positive Y euler angle when the face turns toward the right of the
        // (unmirrored) analysis image, which is the user's own left: the same convention
        // FaceFrame.headYawDegrees documents, so it passes through unchanged.
        headYawDegrees = primary?.headEulerAngleY,
    )
}
