package com.example.lectoryape.utils

import com.example.lectoryape.models.YapeNotificationRaw
import android.service.notification.StatusBarNotification
import android.util.Log

object YapeParser {
    // Regex mantenido (es bueno), pero la clave será qué texto le pasamos
    private val YAPE_REGEX = Regex("""(.+?)\s+te\s+envi(?:ó|Ã³|o)\s+un\s+pago\s+por\s+S/\s*([\d,]+(?:\.\d+)?).*?seguridad\s+es:\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(sbn: StatusBarNotification): YapeNotificationRaw? {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "Yape"

        // CORRECCIÓN CLAVE 1: Priorizar 'bigText'.
        // A veces 'text' viene cortado ("...") y por eso el Regex falla.
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val normalText = extras.getString("android.text")

        // Usamos bigText si existe, si no, el normal.
        val textToParse = if (!bigText.isNullOrEmpty()) bigText else (normalText ?: "")

        // Log de diagnóstico para ver EXACTAMENTE qué está intentando leer
        Log.d("YapeParser", "Analizando texto: '$textToParse'")

        val matchResult = YAPE_REGEX.find(textToParse)

        if (matchResult == null) {
            Log.e("YapeParser", "❌ FALLÓ EL REGEX. El texto no coincide con el patrón esperado.")
            return null
        }

        val (nombre, montoStr, codigo) = matchResult.destructured

        // CORRECCIÓN CLAVE 2: Limpieza agresiva del monto
        // 1. Quitar comas (miles)
        // 2. Quitar espacios
        // 3. Quitar punto final si se coló ("12." -> "12")
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