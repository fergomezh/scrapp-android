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
        mavenCentral() // <-- Aquí es donde Gradle descargará Vico y Coil sin restricciones
    }
}

rootProject.name = "Scrapp"
include(":app")
