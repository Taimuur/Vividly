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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Decodes [file] and applies its EXIF orientation, so a portrait capture is not shown on its side.
 *
 * Returns `null` if the file cannot be decoded, which the caller renders as "still loading" rather
 * than crashing the sample.
 */
internal fun loadUprightBitmap(file: File): ImageBitmap? {
    // Captures are full sensor resolution; sampling keeps a preview off the large-object heap.
    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

    val rotationDegrees = when (
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    if (rotationDegrees == 0f) return decoded.asImageBitmap()

    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    val rotated = Bitmap.createBitmap(
        decoded,
        0,
        0,
        decoded.width,
        decoded.height,
        matrix,
        true,
    )
    if (rotated != decoded) decoded.recycle()
    return rotated.asImageBitmap()
}
