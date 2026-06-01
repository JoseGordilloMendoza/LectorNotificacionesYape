package com.example.kajaapp.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceUtils {

    /**
     * Devuelve el ANDROID_ID que es único por dispositivo tras un reseteo de fábrica
     */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
    }

    /**
     * Devuelve el nombre del modelo del teléfono, ej. "Samsung SM-G998B"
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        if (model.startsWith(manufacturer)) {
            return capitalize(model)
        }
        return capitalize(manufacturer) + " " + model
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            Character.toUpperCase(first) + s.substring(1)
        }
    }
}
