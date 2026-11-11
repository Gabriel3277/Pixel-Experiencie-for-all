pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PixelExperienceForAll"

// Parte 1
include(":app")
include(":core")
include(":themes")

// Módulos planejados (entram nas próximas partes, um por vez):
// include(":launcher")
// include(":widgets")
// include(":controlcenter")
// include(":notifications")
// include(":lockscreen")
// include(":sounds")
// include(":extras")
