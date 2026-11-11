package com.pixelexperience.forall.controlcenter

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ControlCenterStatus {

    /** true se o usuário ativou o serviço em Configurações > Acessibilidade. */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ControlCenterService::class.java)
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return setting.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** true se o app pode alterar brilho e rotação (permissão especial WRITE_SETTINGS). */
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)
}
