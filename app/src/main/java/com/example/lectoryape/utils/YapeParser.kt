package com.example.lectoryape.utils

import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.lectoryape.models.YapeNotificationRaw

object YapeParser {
    // expresión regular para capturar los datos de la notificación
    private val YAPE_REGEX = Regex(
        """(.+?)\s+te\s+envi(?:ó|Ã³)\s+un\s+pago\s+por\s+S/\s*([\d,.]+).*?seguridad\s+es:\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sbn: StatusBarNotification): YapeNotificationRaw? {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""

        // si el título no parece una confirmación de pago, lo descartamos rápido
        if (!title.contains("Confirmación", ignoreCase = true) &&
            !title.contains("Pago", ignoreCase = true)) {
            return null
        }

        // 2. extracción de datos del cuerpo del mensaje
        val matchResult = YAPE_REGEX.find(text) ?: return null

        val (nombre, montoStr, codigo) = matchResult.destructured

        val montoLimpio = montoStr
            .replace(",", "")
            .trimEnd('.')
            .toDoubleOrNull() ?: 0.0

        // 3. RETORNO DEL MODELO LIMPIO
        return YapeNotificationRaw(
            title = title,
            name = nombre.trim(),
            amount = montoLimpio,
            timestamp = sbn.postTime,
            securityCode = codigo,
            notificationId = sbn.id
        )
    }
}