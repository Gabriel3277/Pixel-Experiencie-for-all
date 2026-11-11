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

// Parte 2
include(":launcher")

// Parte 3
include(":controlcenter")

// Módulos planejados (entram nas próximas partes, um por vez):
// include(":widgets")
// include(":notifications")
// include(":lockscreen")
// include(":sounds")
// include(":extras")
