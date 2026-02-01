package com.example.lectoryape.utils

import com.example.lectoryape.models.YapeNotificationRaw
import android.service.notification.StatusBarNotification
import android.util.Log

    // yape a yape 
    private val YAPE_REGEX = Regex("""(.+?)\s+te\s+envi(?:ó|Ã³|o)\s+un\s+pago\s+por\s+S/\s*([\d,]+(?:\.\d+)?).*?seguridad\s+es:\s*(\d+)""", RegexOption.IGNORE_CASE)
    
    // BCP a yape
    private val BCP_REGEX = Regex("""Yape!\s+(.+?)\s+te\s+envi(?:ó|Ã³|o)\s+un\s+pago\s+por\s+S/\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parse(sbn: StatusBarNotification): YapeNotificationRaw? {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "Yape"

        // Priorizar 'bigText'
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val normalText = extras.getString("android.text")
        val textToParse = if (!bigText.isNullOrEmpty()) bigText else (normalText ?: "")

        Log.d("YapeParser", "Analizando texto: '$textToParse'")

        // 1. Intentar Regex Estándar (Yape a Yape)
        var matchResult = YAPE_REGEX.find(textToParse)
        var codigo = "SIN_CODIGO" // Valor por defecto para BCP

        if (matchResult != null) {
            // Es un Yape normal con código
            val (n, m, c) = matchResult.destructured
            procesarYape(sbn, title, n, m, c)
        } else {
            // 2. Intentar Regex BCP (banca movil a Yape)
            matchResult = BCP_REGEX.find(textToParse)
            if (matchResult != null) {
                // Es un Yape de BCP (Sin código)
                val (n, m) = matchResult.destructured
                Log.d("YapeParser", "✅ Encontrado patrón BCP/Banco")
                procesarYape(sbn, title, n, m, codigo)
            } else {
                Log.e("YapeParser", "❌ FALLÓ EL REGEX. No coincide con ningún patrón conocido.")
                null
            }
        }
    }

    private fun procesarYape(sbn: StatusBarNotification, title: String, nombre: String, montoStr: String, codigo: String): YapeNotificationRaw {
        // Limpieza del monto
        val montoLimpio = montoStr.trim().replace(",", "").removeSuffix(".")
        val montoFinal = montoLimpio.toDoubleOrNull() ?: 0.0

        return YapeNotificationRaw(
            title = title,
            name = nombre.trim(),
            amount = montoFinal,
            timestamp = sbn.postTime,
            securityCode = codigo.trim(),
            notificationId = sbn.id
        )
    }
}