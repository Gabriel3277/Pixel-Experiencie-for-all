# Módulo lockscreen — integração

1. settings.gradle.kts:   include(":lockscreen")
2. app/build.gradle.kts:  implementation(project(":lockscreen"))
3. Abrir os ajustes (ex.: botão no Core):  LockScreenModule.openSettings(context)
   (ou embutir o composable LockSettingsScreen(onBack) na navegação do Core)
4. No início do app (opcional): LockScreenModule.sync(context)

Requisitos do projeto: plugin org.jetbrains.kotlin.plugin.compose (Kotlin 2.x), minSdk 34, compileSdk 35.
Se o seu minSdk for menor que 34, altere em lockscreen/build.gradle.kts.
