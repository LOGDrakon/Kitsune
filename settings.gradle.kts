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
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KitsuneV2"

include(":app")
include(":core:common")
include(":core:security")
include(":core:data")
include(":core:network")
include(":core:background")
include(":core:moderation")
include(":core:memory")
include(":core:designsystem")
include(":core:diagnostics")
include(":feature:onboarding")
include(":feature:auth")
include(":feature:persona")
include(":feature:settings")
include(":feature:chat")
include(":core:models")
include(":core:backend")
include(":core:billing")
include(":feature:universe")
include(":feature:store")
include(":feature:marketplace")
include(":feature:cosmetics")
include(":core:transfer")
