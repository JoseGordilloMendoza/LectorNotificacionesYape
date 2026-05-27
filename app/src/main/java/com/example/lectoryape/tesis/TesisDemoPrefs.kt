package com.example.lectoryape.tesis

import android.content.Context
import com.example.lectoryape.repository.FakeTesisRepository

object TesisDemoPrefs {
    private const val PREFS_NAME = "tesis_demo_prefs"
    private const val KEY_ACTIVE_STALL = "active_stall"

    fun getActiveStall(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_STALL, FakeTesisRepository.getDefaultActiveStallName())
            ?: FakeTesisRepository.getDefaultActiveStallName()
    }

    fun setActiveStall(context: Context, stallName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_STALL, stallName)
            .apply()
    }
}
