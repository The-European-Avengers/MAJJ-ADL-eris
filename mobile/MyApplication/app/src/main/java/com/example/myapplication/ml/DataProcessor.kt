package com.example.myapplication

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Representa una fila limpia de tu CSV
data class CarbonRecord(
    val datetime: LocalDateTime,
    val carbonIntensity: Float
)

class FeatureProcessor {

    fun processData(rawRecords: List<CarbonRecord>): Array<Array<FloatArray>>? {
        // Validación de seguridad: necesitamos al menos 360 horas de historial
        // (336 para la ventana + 24 para calcular la primera derivada diaria)
        if (rawRecords.size < 360) {
            return null 
        }

        // Nos quedamos exactamente con los 360 registros más recientes
        val recentRecords = rawRecords.takeLast(360)
        
        // Preparamos la matriz vacía de 336 x 7
        val featuresArray = Array(336) { FloatArray(7) }

        for (i in 0 until 336) {
            // Desplazamos el índice 24 posiciones para tener margen de cálculo hacia atrás
            val currentIndex = i + 24 
            val currentRecord = recentRecords[currentIndex]
            
            // 1. Target Col (Intensidad actual)
            val targetCol = currentRecord.carbonIntensity
            
            // 2. diff_1 (Diferencia respecto a la hora anterior)
            val diff1 = targetCol - recentRecords[currentIndex - 1].carbonIntensity
            
            // 3. diff_24 (Diferencia respecto a ayer a esta misma hora)
            val diff24 = targetCol - recentRecords[currentIndex - 24].carbonIntensity
            
            // 4 y 5. Seno y Coseno de la hora (Ciclo diario de 0 a 23)
            val hour = currentRecord.datetime.hour
            val hourSin = sin(2 * PI * hour / 24.0).toFloat()
            val hourCos = cos(2 * PI * hour / 24.0).toFloat()
            
            // 6 y 7. Seno y Coseno del año (Ciclo estacional de 1 a 365)
            // Nota: Se asume 365 días como estándar para el modelo ML
            val dayOfYear = currentRecord.datetime.dayOfYear
            val yearSin = sin(2 * PI * dayOfYear / 365.0).toFloat()
            val yearCos = cos(2 * PI * dayOfYear / 365.0).toFloat()
            
            // Llenamos la fila 'i' con las 7 características exactas
            featuresArray[i] = floatArrayOf(
                targetCol,
                diff1,
                diff24,
                hourSin,
                hourCos,
                yearSin,
                yearCos
            )
        }
        
        // Devolvemos la matriz envuelta en un array exterior para lograr la forma [1, 336, 7]
        return arrayOf(featuresArray)
    }



fun parseCsvToRecords(csvContent: String): List<CarbonRecord> {
        val records = mutableListOf<CarbonRecord>()
        // Formato exacto que viene en tu CSV (ej: 2025-01-01T00:00:00.000000)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

        val lines = csvContent.lines()
        // Empezamos desde 1 para saltar la fila de las cabeceras
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isNotEmpty()) {
                val columns = line.split(",")
                if (columns.size >= 5) {
                    try {
                        val datetime = LocalDateTime.parse(columns[0], formatter)
                        val intensity = columns[4].toFloat()
                        records.add(CarbonRecord(datetime, intensity))
                    } catch (e: Exception) {
                        // Ignorar líneas corruptas o mal formateadas
                    }
                }
            }
        }
        // Aseguramos que estén en orden cronológico (de más antiguo a más reciente)
        return records.sortedBy { it.datetime }
    }
}
