package com.pixelexperience.forall.controlcenter

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda o último erro que derrubou o app, para ele poder ser lido na tela do módulo
 * (sem PC e sem adb). Depois de gravar, deixa o Android tratar o erro como sempre.
 */
internal object CrashLog {
    private const val FILE = "cc_last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
                file.writeText("$time  [${thread.name}]\n" + error.stackTraceToString())
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) f.readText().takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
