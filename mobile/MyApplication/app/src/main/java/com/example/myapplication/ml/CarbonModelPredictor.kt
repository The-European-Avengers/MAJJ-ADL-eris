package com.example.myapplication // Ajusta a tu paquete

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class CarbonModelPredictor(context: Context) {
    
    private var interpreter: Interpreter? = null

    init {
        // Actualiza el nombre del modelo aquí
        val model = loadModelFile(context, "carbon_model_cnn.tflite")
        val options = Interpreter.Options().apply {
            numThreads = 2 
        }
        interpreter = Interpreter(model, options)

        // Añade estas líneas para leer la estructura real del modelo
        val inputShape = interpreter?.getInputTensor(0)?.shape()
        val outputShape = interpreter?.getOutputTensor(0)?.shape()

        android.util.Log.d("ML_DEBUG", "El modelo ESPERA en la entrada: ${inputShape?.contentToString()}")
        android.util.Log.d("ML_DEBUG", "El modelo DEVUELVE en la salida: ${outputShape?.contentToString()}")
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(inputData: Array<Array<FloatArray>>): FloatArray {

        val outputData = arrayOf(FloatArray(24)) 

        // Ejecutar la inferencia
        interpreter?.run(inputData, outputData)

        return outputData[0]
    }

    fun close() {
        // Siempre es buena práctica cerrar el intérprete cuando ya no se usa
        interpreter?.close()
    }
}