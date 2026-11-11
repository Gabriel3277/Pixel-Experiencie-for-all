# Pixel Experience for All — Parte 1: Pixel Experience Core

Módulos desta parte: `:app`, `:core`, `:themes`. Os demais (launcher, widgets,
controlcenter, notifications, lockscreen, sounds, extras) estão planejados e
listados (comentados) em `settings.gradle.kts`.

## Compilar
- Android Studio (Ladybug+): abra a pasta e sincronize.
- Sem computador: envie o projeto para o GitHub; o workflow `.github/workflows/build.yml`
  gera o APK debug (aba Actions → artefato `pixel-experience-debug`).
- Linha de comando, com Gradle 8.11.1 instalado: `gradle :app:assembleDebug`.

Requisitos: JDK 17, Android 12+ (API 31) para instalar; alvo Android 14 (API 34).
