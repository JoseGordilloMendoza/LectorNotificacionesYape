package com.example.lectoryape.utils

import com.example.lectoryape.models.YapeNotificationRaw
import android.service.notification.StatusBarNotification

object YapeParser {
    // regex para parsear el texto capturado en notificacion
    private val YAPE_REGEX = Regex("""^(.+?)\s+te\s+envi(?:ó|Ã³)\s+un\s+pago\s+por\s+S/\s*([\d,.]+)""", RegexOption.IGNORE_CASE)

    fun parse(content: StatusBarNotification): YapeNotificationRaw? {
        val extras = sbn.notification.extras
        val title = extras.getString("andrioid.title") ?: ""

        val matchResult = YAPE_REGEX.find(content) ?: return null

        val (nombre, monto) = matchResult.destructured

        return YapeNotificationRaw(
            nombre = nombre.trim(),
            monto = monto.trim(),
            fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
    }
}