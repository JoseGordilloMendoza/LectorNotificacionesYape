package com.example.lectoryape.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.lectoryape.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Maneja la detección y descarga de actualizaciones OTA (Over-The-Air).
 *
 * Flujo:
 * 1. checkForUpdates() consulta Firestore (app_config/version)
 * 2. Compara latestVersionCode con el versionCode actual del APK
 * 3. Si hay una versión nueva, muestra un AlertDialog
 * 4. Si el usuario acepta, descarga el APK con DownloadManager
 * 5. Al completarse, abre el instalador nativo de Android
 */
class AppUpdater(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "AppUpdater"
        private const val FIRESTORE_COLLECTION = "app_config"
        private const val FIRESTORE_DOCUMENT = "version"
        private const val APK_FILENAME = "lector_yape_update.apk"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    /**
     * Punto de entrada principal. Llama esto desde MainActivity.initializeApp()
     * No bloquea la UI, corre en segundo plano silenciosamente.
     */
    fun checkForUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 Verificando actualizaciones disponibles...")
                val versionInfo = fetchVersionInfo() ?: return@launch

                val latestCode = (versionInfo["latestVersionCode"] as? Long)?.toInt() ?: return@launch
                val currentCode = getCurrentVersionCode()

                Log.d(TAG, "📦 Versión actual: $currentCode | Última disponible: $latestCode")

                if (latestCode > currentCode) {
                    val versionName = versionInfo["latestVersionName"] as? String ?: "Nueva versión"
                    val downloadUrl = versionInfo["downloadUrl"] as? String ?: return@launch
                    val releaseNotes = versionInfo["releaseNotes"] as? String ?: "Mejoras generales"
                    val isForce = versionInfo["isForceUpdate"] as? Boolean ?: false

                    withContext(Dispatchers.Main) {
                        // Mostrar primero la tarjeta en la pantalla
                        showUpdateBanner(versionName, releaseNotes, downloadUrl)
                        // Si es obligatoria además mostramos el diálogo bloqueante
                        if (isForce) {
                            showUpdateDialog(versionName, releaseNotes, downloadUrl, isForce = true)
                        }
                    }
                } else {
                    Log.d(TAG, "✅ La app está actualizada, no hay nada que descargar.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al verificar actualizaciones: ${e.message}", e)
                // Fallo silencioso: no bloqueamos al usuario por un error de red
            }
        }
    }

    /**
     * Consulta el documento Firestore con la info de la última versión.
     */
    private suspend fun fetchVersionInfo(): Map<String, Any>? {
        return try {
            val snapshot = firestore
                .collection(FIRESTORE_COLLECTION)
                .document(FIRESTORE_DOCUMENT)
                .get()
                .await()
            snapshot.data
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error consultando Firestore para versión: ${e.message}")
            null
        }
    }

    /**
     * Obtiene el versionCode del APK instalado actualmente.
     */
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo versionCode actual: ${e.message}")
            0
        }
    }

    /**
     * Muestra la tarjeta (banner) de actualización dentro del layout de MainActivity.
     * Es discreta, no interrumpe al usuario pero siempre visible al hacer scroll.
     */
    fun showUpdateBanner(versionName: String, releaseNotes: String, downloadUrl: String) {
        try {
            val card = activity.findViewById<androidx.cardview.widget.CardView>(R.id.cardUpdateBanner)
            val tvVersion = activity.findViewById<android.widget.TextView>(R.id.tvUpdateVersion)
            val tvNotes = activity.findViewById<android.widget.TextView>(R.id.tvUpdateReleaseNotes)
            val btnInstall = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnInstallUpdate)

            tvVersion.text = "Versión $versionName"
            tvNotes.text = releaseNotes

            btnInstall.setOnClickListener {
                btnInstall.isEnabled = false
                btnInstall.text = "Descargando..."
                startDownload(downloadUrl)
            }

            // Animar la aparición de la tarjeta suavemente
            card.visibility = View.VISIBLE
            card.alpha = 0f
            card.animate().alpha(1f).setDuration(500).start()

            Log.d(TAG, "📳 Tarjeta de actualización mostrada para versión $versionName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando tarjeta de actualización: ${e.message}")
        }
    }

    /**
     * Muestra el diálogo de actualización disponible.
     * Usado solo si isForce=true (actualización obligatoria).
     */
    private fun showUpdateDialog(
        versionName: String,
        releaseNotes: String,
        downloadUrl: String,
        isForce: Boolean
    ) {
        val builder = AlertDialog.Builder(activity)
            .setTitle("🚀 Actualización Disponible")
            .setMessage("Hay una nueva versión ($versionName) lista para instalar.\n\n📋 Novedades:\n$releaseNotes")
            .setCancelable(!isForce)
            .setPositiveButton("⬇️ Actualizar Ahora") { dialog, _ ->
                dialog.dismiss()
                startDownload(downloadUrl)
            }

        if (!isForce) {
            builder.setNegativeButton("Más tarde", null)
        }

        builder.show()
        Log.d(TAG, "💬 Diálogo de actualización mostrado (force=$isForce)")
    }

    /**
     * Referencia las vistas integradas en activity_main.xml para la pestaña "Actualizaciones"
     */
    fun setupUpdatesScreen() {
        CoroutineScope(Dispatchers.IO).launch {
            val versionInfo = fetchVersionInfo()

            withContext(Dispatchers.Main) {
                // Referencias a las vistas del layout ya presentes en activity
                val tvStatus       = activity.findViewById<android.widget.TextView>(R.id.tvDialogStatus)
                val tvCurrentVer   = activity.findViewById<android.widget.TextView>(R.id.tvDialogCurrentVersion)
                val tvCurrentBuild = activity.findViewById<android.widget.TextView>(R.id.tvDialogCurrentBuild)
                val tvLatestVer    = activity.findViewById<android.widget.TextView>(R.id.tvDialogLatestVersion)
                val tvLatestBuild  = activity.findViewById<android.widget.TextView>(R.id.tvDialogLatestBuild)
                val tvNotes        = activity.findViewById<android.widget.TextView>(R.id.tvDialogReleaseNotes)
                val btnAction      = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogAction)
                val btnDismiss     = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogDismiss)

                val currentCode = getCurrentVersionCode()
                val currentName = getCurrentVersionName()

                // Rellenar siempre los datos de la versión instalada
                tvCurrentVer.text   = "v$currentName"
                tvCurrentBuild.text = "build $currentCode"

                if (versionInfo == null) {
                    // Sin conexión: mostrar solo versión local
                    tvStatus.text       = "⚠️ Sin conexión a internet"
                    tvLatestVer.text    = "—"
                    tvLatestBuild.text  = "—"
                    tvNotes.text        = "No se pudo obtener información de actualizaciones."
                    btnAction.visibility = android.view.View.GONE
                    return@withContext
                }

                val latestCode   = (versionInfo["latestVersionCode"] as? Long)?.toInt() ?: 0
                val latestName   = versionInfo["latestVersionName"] as? String ?: "—"
                val downloadUrl  = versionInfo["downloadUrl"] as? String ?: ""
                val releaseNotes = versionInfo["releaseNotes"] as? String ?: "Sin notas disponibles."
                val isUpToDate   = currentCode >= latestCode

                // Rellenar versión de Firestore
                tvLatestVer.text    = "v$latestName"
                tvLatestBuild.text  = "build $latestCode"
                tvNotes.text        = releaseNotes

                if (isUpToDate) {
                    tvStatus.text  = "✅ Tienes la versión más reciente"
                    btnAction.visibility = android.view.View.GONE
                    // btnDismiss permanece GONE
                } else {
                    tvStatus.text  = "🔴 Hay una nueva versión disponible"
                    btnAction.text = "⬇  Instalar v$latestName"
                    btnDismiss.visibility = android.view.View.VISIBLE

                    btnAction.visibility = android.view.View.VISIBLE
                    btnAction.setOnClickListener {
                        startDownload(downloadUrl)
                    }
                    // btnDismiss is unused in screen mode actually
                }

                Log.d(TAG, "ℹ️ Pantalla de actualizaciones refrescada (upToDate=$isUpToDate)")
            }
        }
    }

    /** Obtiene el versionName (ej. "1.7") instalado actualmente. */
    private fun getCurrentVersionName(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
    }

    /**
     * Inicia la descarga del APK usando el DownloadManager del sistema.
     * La descarga aparece en la barra de notificaciones del sistema.
     */
    private fun startDownload(downloadUrl: String) {
        try {
            // Limpiar descarga anterior si existe
            val oldApk = File(activity.cacheDir, "updates/$APK_FILENAME")
            if (oldApk.exists()) oldApk.delete()
            oldApk.parentFile?.mkdirs()

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Actualizando Lector Yape")
                setDescription("Descargando nueva versión...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILENAME
                )
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            Log.d(TAG, "⬇️ Descarga iniciada, downloadId=$downloadId")
            registerDownloadReceiver()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando descarga: ${e.message}", e)
        }
    }

    /**
     * Registra un BroadcastReceiver temporal que escucha cuando el DownloadManager
     * termina de descargar el archivo. Al completarse, lanza el instalador.
     */
    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId == downloadId) {
                    Log.d(TAG, "✅ Descarga completada! Abriendo instalador...")
                    activity.runOnUiThread { promptInstall() }
                    unregisterDownloadReceiver()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            activity.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    /**
     * Abre el instalador nativo de Android para que el usuario confirme la instalación.
     * Funciona en Android 7.0+ usando FileProvider.
     */
    private fun promptInstall() {
        try {
            val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val uriString = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                )
                cursor.close()

                val apkUri = Uri.parse(uriString)

                // En Android 7+ necesitamos convertir a content:// URI con FileProvider
                val installUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val apkFile = File(apkUri.path!!)
                    androidx.core.content.FileProvider.getUriForFile(
                        activity,
                        "${activity.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    apkUri
                }

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(installUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                activity.startActivity(intent)
                Log.d(TAG, "📲 Instalador lanzado correctamente")
            } else {
                cursor.close()
                Log.e(TAG, "❌ No se encontró el archivo descargado en DownloadManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error abriendo instalador: ${e.message}", e)
        }
    }

    /**
     * Limpia el BroadcastReceiver para evitar memory leaks.
     * Llama esto desde onDestroy() de MainActivity.
     */
    fun cleanup() {
        unregisterDownloadReceiver()
    }

    private fun unregisterDownloadReceiver() {
        try {
            downloadReceiver?.let { activity.unregisterReceiver(it) }
            downloadReceiver = null
        } catch (e: IllegalArgumentException) {
            // ya estaba desregistrado
        }
    }
}
