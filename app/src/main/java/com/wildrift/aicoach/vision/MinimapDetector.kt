package com.wildrift.aicoach.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wildrift.aicoach.model.MinimapDetection
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class MinimapDetector(context: Context, modelAssetName: String = "yolov8n_wr_minimap_int8.tflite") {
    private var interpreter: Interpreter? = null
    private var channelFirst = false
    private var normalizedCoordinates = true
    var status = "Minimapa: modelo não incluído; detecção indisponível"
        private set

    init {
        var loaded: Interpreter? = null
        try {
            if (context.assets.list("")?.contains(modelAssetName) == true) {
                val metadata = context.assets.open("minimap_model.json").bufferedReader().use { JSONObject(it.readText()) }
                require(metadata.getInt("schemaVersion") == 1)
                val classes = metadata.getJSONArray("classes")
                val expected = listOf("aliado", "inimigo", "objetivo_ativo", "onda_minions")
                require(classes.length() == 4 && expected.indices.all { classes.getString(it) == expected[it] })
                val units = metadata.getString("coordinates")
                require(units == "normalized" || units == "pixels")
                normalizedCoordinates = units == "normalized"
                context.assets.openFd(modelAssetName).use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { stream ->
                        val model = stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                        loaded = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
                    }
                }
                val engine = requireNotNull(loaded)
                require(engine.inputTensorCount == 1 && engine.outputTensorCount == 1)
                val input = engine.getInputTensor(0)
                val shape = input.shape()
                channelFirst = shape.contentEquals(intArrayOf(1, 3, 320, 320))
                require(channelFirst || shape.contentEquals(intArrayOf(1, 320, 320, 3))) { "Expected 320x320 RGB input" }
                encoding(input)
                val output = engine.getOutputTensor(0)
                val outShape = output.shape()
                require(outShape.size == 3 && outShape[0] == 1 && (outShape[1] == 8 || outShape[2] == 8))
                encoding(output)
                interpreter = engine
                status = "Minimapa: modelo carregado (CPU); precisão requer validação"
            }
        } catch (e: Exception) {
            loaded?.close()
            status = "Minimapa: modelo ou metadados incompatíveis"
            Log.w("MinimapDetector", status, e)
        }
    }

    private fun encoding(tensor: Tensor): TensorEncoding = when (tensor.dataType()) {
        DataType.FLOAT32 -> TensorEncoding.FLOAT32
        DataType.UINT8 -> TensorEncoding.UINT8
        DataType.INT8 -> TensorEncoding.INT8
        else -> error("Unsupported tensor type: ${tensor.dataType()}")
    }

    fun detect(bitmap: Bitmap): List<MinimapDetection> {
        val engine = interpreter ?: return emptyList()
        return try {
            require(bitmap.width == 320 && bitmap.height == 320)
            val input = engine.getInputTensor(0)
            val output = engine.getOutputTensor(0)
            val pixels = IntArray(320 * 320)
            bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 320)
            val inputQuant = input.quantizationParams()
            val inputBuffer = MinimapTensorCodec.packRgb(pixels, channelFirst, encoding(input), inputQuant.scale, inputQuant.zeroPoint)
            require(inputBuffer.capacity() == input.numBytes())
            val outputBuffer = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
            engine.run(inputBuffer, outputBuffer)
            val outputQuant = output.quantizationParams()
            val values = MinimapTensorCodec.unpack(outputBuffer, encoding(output), outputQuant.scale, outputQuant.zeroPoint)
            MinimapYoloDecoder.decode(values, output.shape(), normalizedCoordinates)
        } catch (e: Exception) {
            status = "Minimapa: falha de inferência; detecção indisponível"
            Log.w("MinimapDetector", status, e)
            emptyList()
        }
    }

    fun close() { interpreter?.close(); interpreter = null }
}
