package com.hwb.aianswerer.chat.capture

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.hwb.aianswerer.chat.model.NodeSource
import com.hwb.aianswerer.chat.model.ScreenNode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OcrReader {
    companion object {
        // ScreenCaptureEngine is short-lived. Load and reuse OCR only when a fallback actually needs it.
        private val recognizer by lazy {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
    }

    suspend fun read(bitmap: Bitmap, packageName: String): List<ScreenNode> = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (!continuation.isActive) return@addOnSuccessListener
                continuation.resume(result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    ScreenNode(line.text.trim(), Rect(box), packageName, source = NodeSource.OCR)
                }.filter { it.text.isNotBlank() })
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
            .addOnCanceledListener { if (continuation.isActive) continuation.resume(emptyList()) }
    }
}
