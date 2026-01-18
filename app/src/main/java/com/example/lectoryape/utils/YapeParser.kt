package com.example.lectoryape.utils

import com.example.lectoryape.models.YapeNotificationRaw
import android.service.notification.StatusBarNotification

object YapeParser {
    // regex para parsear el texto capturado en notificacion
    private val YAPE_REGEX = Regex("""^(.+?)\s+te\s+envi(?:ó|Ã³)\s+un\s+pago\s+por\s+S/\s*([\d,.]+).*?seguridad\s+es:\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(sbn: StatusBarNotification): YapeNotificationRaw? {
        val text = sbn.notification.extras.getString("android.text") ?: ""
        val matchResult = YAPE_REGEX.find(text) ?: return null

        val (titulo, nombre, monto, codigo) = matchResult.destructured

        return YapeNotificationRaw(
            title = titulo.trim(),
            name = nombre.trim(),
            amount = monto.trim().replace(",","").toDoubleOrNull() ?: 0.0,
            timestamp = sbn .postTime,
            securityCode = codigo,
            notificationId = sbn.id
        )
    }
}