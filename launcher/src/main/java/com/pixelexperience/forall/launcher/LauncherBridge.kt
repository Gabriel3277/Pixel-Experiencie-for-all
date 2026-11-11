package com.pixelexperience.forall.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Ponte para o launcher Lawnchair (fork Pixel). Só usa APIs públicas do Android:
 * lista os apps que respondem à intent HOME, abre um deles e leva o usuário à
 * tela do sistema onde se escolhe o launcher padrão. O Android não permite que
 * um app troque o launcher padrão sozinho.
 */
class LauncherBridge(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun scan(): LauncherStatus {
        val own = appContext.packageName
        val defaultPackage = pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

        val launchers = pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.packageName != own }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledLauncher(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    homeActivity = info.name,
                    isLawnchair = info.packageName.startsWith(LAWNCHAIR_PREFIX),
                    isDefault = info.packageName == defaultPackage,
                )
            }
            .sortedWith(compareByDescending<InstalledLauncher> { it.isLawnchair }.thenBy { it.label.lowercase() })

        return LauncherStatus(launchers)
    }

    /** Abre o launcher: usa a entrada normal do app e, sem ela, a activity HOME. */
    fun openIntent(launcher: InstalledLauncher): Intent =
        pm.getLaunchIntentForPackage(launcher.packageName)
            ?: homeIntent().setClassName(launcher.packageName, launcher.homeActivity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Tela do sistema para escolher o app de tela inicial padrão. */
    fun defaultHomeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun fallbackSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)

    private companion object {
        const val LAWNCHAIR_PREFIX = "app.lawnchair"
    }
}
