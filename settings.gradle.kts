pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = uri("https://jitpack.io") }

        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            //from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "PersonalSchedule"
include(":app")
 