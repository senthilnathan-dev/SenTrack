pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SenTrack"
include(":app")
include(":core-security")
include(":core-data")
include(":core-sync")
include(":core-ui")
include(":core-alerts")
include(":core-attachments")
include(":feature-personal")
include(":feature-work")
include(":feature-reminders")
include(":feature-finance")
