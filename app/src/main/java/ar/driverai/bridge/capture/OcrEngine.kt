package ar.driverai.bridge.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OCR 100 % local (ML Kit con modelo incluido). La imagen nunca sale del teléfono. */
object OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognizeLines(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    .map { it.text }
                cont.resume(lines)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
