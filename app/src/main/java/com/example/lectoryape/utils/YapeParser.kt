package com.example.kajaapp.utils

import com.example.kajaapp.models.YapeNotificationRaw
import android.service.notification.StatusBarNotification
import android.util.Log


object YapeParser {
    // Regex principal (Yape a Yape) - Requiere código de seguridad
    private val YAPE_REGEX = Regex("""(.+?)\s+te\s+envi(?:ó|Ã³|o)\s+un\s+pago\s+por\s+S/\s*([\d,]+(?:\.\d+)?).*?seguridad\s+es:\s*(\d+)""", RegexOption.IGNORE_CASE)
    
    // Regex Secundario (Bancos a Yape: BCP, PLIN, Interbank, etc.)
    // Empiezan con "Yape!" y NO tienen código de seguridad visible en la notificación
    private val BANK_REGEX = Regex("""Yape!\s+(.+?)\s+te\s+envi(?:ó|Ã³|o)\s+un\s+pago\s+por\s+S/\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    // Regex Plin (Interbank) - Formato: "Nombre te ha plineado S/ Monto"
    private val PLIN_REGEX = Regex("""(.+?)\s+te\s+ha\s+plineado\s+S/\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parse(sbn: StatusBarNotification): YapeNotificationRaw? {
        val extras = sbn.notification.extras
        // Para Plin, el título suele ser "Interbank" o el nombre de la app
        var title = extras.getString("android.title") ?: "Yape"

        // Priorizar 'bigText'
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val normalText = extras.getString("android.text")
        val textToParse = if (!bigText.isNullOrEmpty()) bigText else (normalText ?: "")

        Log.d("YapeParser", "Analizando texto: '$textToParse'")

        // 1. Intentar Regex Estándar (Yape a Yape)
        var matchResult = YAPE_REGEX.find(textToParse)

        if (matchResult != null) {
            // Es un Yape normal con código
            val (n, m, c) = matchResult.destructured
            return procesarYape(sbn, title, n, m, c, "YAPE")
        } 
        
        // 2. Intentar Regex Bancos (BCP, PLIN, etc.)
        matchResult = BANK_REGEX.find(textToParse)
        if (matchResult != null) {
            val (n, m) = matchResult.destructured
            Log.d("YapeParser", "✅ Encontrado patrón Banco (BCP/PLIN)")
            return procesarYape(sbn, title, n, m, "", "YAPE")
        }

        // 3. Intentar Regex Plin (Interbank)
        matchResult = PLIN_REGEX.find(textToParse)
        if (matchResult != null) {
            val (n, m) = matchResult.destructured
            if (title.equals("Interbank", ignoreCase = true)) {
                title = "Plin"
            }
            Log.d("YapeParser", "✅ Encontrado patrón Plin")
            return procesarYape(sbn, title, n, m, "", "PLIN")
        } 
            
        Log.e("YapeParser", "❌ FALLÓ EL REGEX. No coincide con ningún patrón conocido.")
        return null
    }

    private fun procesarYape(sbn: StatusBarNotification, title: String, nombre: String, montoStr: String, codigo: String, walletType: String): YapeNotificationRaw {
        // Limpieza del monto
        val montoLimpio = montoStr.trim().replace(",", "").removeSuffix(".")
        val montoFinal = montoLimpio.toDoubleOrNull() ?: 0.0

        return YapeNotificationRaw(
            title = title,
            name = nombre.trim(),
            amount = montoFinal,
            timestamp = sbn.postTime,
            securityCode = codigo.trim(),
            notificationId = sbn.id,
            walletType = walletType
        )
    }
}