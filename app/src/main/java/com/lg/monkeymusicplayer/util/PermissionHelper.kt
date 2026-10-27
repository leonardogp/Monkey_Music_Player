package com.lg.monkeymusicplayer.util
 
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
 
/**
 * Centraliza la lógica de permisos de la app.
 *
 * Separación de responsabilidades:
 *  - [getRequiredReadPermissions]  → permisos para LEER archivos de audio (scan de biblioteca).
 *  - [hasManageExternalStorage]    → permiso para ESCRIBIR tags ID3 al filesystem (Android 11+).
 *
 * MANAGE_EXTERNAL_STORAGE es un permiso muy invasivo que Google restringe en Play Store.
 * Se solicita solo cuando el usuario explícitamente quiere editar tags, no al arrancar.
 */
object PermissionHelper {
 
    /**
     * Permisos necesarios para escanear y reproducir audio.
     * MANAGE_EXTERNAL_STORAGE NO está aquí; se gestiona por separado en [hasManageExternalStorage].
     */
    fun getRequiredReadPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ (API 33+): READ_MEDIA_AUDIO + notificaciones.
                // READ_MEDIA_VISUAL_USER_SELECTED se declara en el Manifest pero NO se solicita
                // aquí — el sistema lo otorga automáticamente cuando el usuario elige acceso
                // parcial a medios. Incluirlo en requestPermissions() causa que aparezca como
                // "Sin permiso" en ajustes y bloquea el arranque de la app.
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12: READ_EXTERNAL_STORAGE con maxSdkVersion=32
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // Android 10 y anteriores
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
 
    /** Verifica si todos los permisos de lectura están concedidos. */
    fun hasReadPermissions(context: Context): Boolean {
        return getRequiredReadPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
 
    /**
     * Verifica si MANAGE_EXTERNAL_STORAGE está concedido (necesario para escribir tags ID3).
     *
     * En Android < 11 devuelve true porque WRITE_EXTERNAL_STORAGE (declarado en el Manifest
     * con maxSdkVersion=32) es suficiente para escribir archivos.
     */
    fun hasManageExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // WRITE_EXTERNAL_STORAGE cubre la escritura en versiones anteriores
        }
    }
 
    /** @deprecated Usa [hasReadPermissions]. Mantenido por compatibilidad con código existente. */
    @Deprecated("Use hasReadPermissions", ReplaceWith("hasReadPermissions(context)"))
    fun hasAudioPermissions(context: Context): Boolean = hasReadPermissions(context)
 
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
 
    fun getNotificationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }
 
    fun getForegroundServicePermission(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
        } else {
            emptyArray()
        }
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }

    fun getRecordAudioPermission(): Array<String> {
        return arrayOf(Manifest.permission.RECORD_AUDIO)
    }
}