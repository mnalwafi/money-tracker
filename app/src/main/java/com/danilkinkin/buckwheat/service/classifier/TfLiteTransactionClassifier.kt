package com.danilkinkin.buckwheat.service.classifier

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Singleton
class TfLiteTransactionClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleBasedFallback: RuleBasedClassifierFallback,
) : TransactionClassifier {

    companion object {
        private const val TAG = "TfLiteClassifier"
        private const val MODEL_FILENAME = "transaction_classifier.tflite"
        private const val VOCAB_FILENAME = "vocab.txt"
        private const val MAX_SEQ_LEN = 32

        // Class index mapping: 0: EXPENSE, 1: INCOME, 2: NOISE
        private val INDEX_TO_CLASS = mapOf(
            0 to TransactionClassificationType.EXPENSE,
            1 to TransactionClassificationType.INCOME,
            2 to TransactionClassificationType.NOISE
        )
    }

    private var interpreter: Interpreter? = null
    private val vocab = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile
    private var isModelReady = false

    private val classifierScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val initJob: kotlinx.coroutines.Job = classifierScope.launch {
        initializeEngine()
    }

    suspend fun awaitInitialization() {
        initJob.join()
    }

    private fun initializeEngine() {
        try {
            loadVocabulary()
            val modelBuffer = loadModelFile()
            if (modelBuffer != null) {
                val options = Interpreter.Options()
                options.setNumThreads(2)
                synchronized(this) {
                    interpreter = Interpreter(modelBuffer, options)
                    isModelReady = true
                }
                Log.i(TAG, "TFLite model successfully initialized on background thread.")
            } else {
                Log.w(TAG, "Model buffer is null, falling back to rule-based classifier.")
                isModelReady = false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "TFLite initialization failed, using rule-based fallback: ${t.message}")
            synchronized(this) {
                interpreter = null
                isModelReady = false
            }
        }
    }

    private fun loadVocabulary() {
        try {
            context.assets.open(VOCAB_FILENAME).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEachIndexed { index, word ->
                        val trimmed = word.trim().lowercase(Locale.ROOT)
                        if (trimmed.isNotEmpty()) {
                            vocab[trimmed] = index
                        }
                    }
                }
            }
            Log.d(TAG, "Loaded vocabulary with ${vocab.size} words.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load vocabulary file: ${e.message}")
        }
    }

    private fun loadModelFile(): ByteBuffer? {
        return try {
            val fileDescriptor: AssetFileDescriptor = context.assets.openFd(MODEL_FILENAME)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel: FileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.w(TAG, "Model file not found or inaccessible in assets: ${e.message}")
            null
        }
    }

    override fun classify(text: String): ClassificationResult {
        if (!isModelReady || interpreter == null || vocab.isEmpty()) {
            return ruleBasedFallback.classify(text)
        }

        return try {
            val inputTensor = preprocessText(text)
            val outputScores = Array(1) { FloatArray(3) }
            synchronized(this) {
                interpreter?.run(inputTensor, outputScores)
            }

            val rawScores = outputScores[0]
            val probabilities = softmax(rawScores)

            val pExpense = probabilities[0]
            val pIncome = probabilities[1]
            val pNoise = probabilities[2]

            val probMap = mapOf(
                TransactionClassificationType.EXPENSE to pExpense,
                TransactionClassificationType.INCOME to pIncome,
                TransactionClassificationType.NOISE to pNoise
            )

            val predictedType = when {
                pExpense >= pIncome && pExpense >= pNoise -> TransactionClassificationType.EXPENSE
                pIncome >= pExpense && pIncome >= pNoise -> TransactionClassificationType.INCOME
                else -> TransactionClassificationType.NOISE
            }

            val confidence = probMap[predictedType] ?: 0.5f

            ClassificationResult(
                type = predictedType,
                confidence = confidence,
                probabilities = probMap,
                source = "tflite"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Inference error in TFLite model, falling back to rule-based engine", t)
            ruleBasedFallback.classify(text)
        }
    }

    suspend fun classifySuspend(text: String): ClassificationResult {
        if (!isModelReady && initJob.isActive) {
            initJob.join()
        }
        return classify(text)
    }

    private fun preprocessText(text: String): Array<IntArray> {
        val tokens = text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        val unkToken = vocab["<UNK>"] ?: 2
        val input = IntArray(MAX_SEQ_LEN) { 0 } // 0 is <PAD>

        for (i in 0 until minOf(tokens.size, MAX_SEQ_LEN)) {
            val word = tokens[i]
            input[i] = vocab[word] ?: unkToken
        }

        return arrayOf(input)
    }

    private fun softmax(scores: FloatArray): FloatArray {
        var max = Float.NEGATIVE_INFINITY
        for (v in scores) {
            if (v > max) max = v
        }

        var sum = 0f
        val expScores = FloatArray(scores.size)
        for (i in scores.indices) {
            val e = exp(scores[i] - max)
            expScores[i] = e
            sum += e
        }

        val result = FloatArray(scores.size)
        if (sum > 0f) {
            for (i in scores.indices) {
                result[i] = expScores[i] / sum
            }
        }
        return result
    }

    fun close() {
        classifierScope.cancel()
        try {
            synchronized(this) {
                interpreter?.close()
                interpreter = null
                isModelReady = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TFLite interpreter", e)
        }
    }
}
