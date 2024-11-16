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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            version("coreKtx", "1.12.0")
            version("junit", "4.13.2")
            version("junitVersion", "1.1.5")
            version("espressoCore", "3.5.1")
            version("lifecycleRuntimeKtx", "2.7.0")
            version("activityCompose", "1.8.2")
            version("composeBom", "2024.02.00")
            version("composeUi", "1.6.1")

            library("androidx-core-ktx", "androidx.core", "core-ktx").versionRef("coreKtx")
            library("junit", "junit", "junit").versionRef("junit")
            library("androidx-junit", "androidx.test.ext", "junit").versionRef("junitVersion")
            library("androidx-espresso-core", "androidx.test.espresso", "espresso-core").versionRef("espressoCore")
            library("androidx-lifecycle-runtime-ktx", "androidx.lifecycle", "lifecycle-runtime-ktx").versionRef("lifecycleRuntimeKtx")
            library("androidx-activity-compose", "androidx.activity", "activity-compose").versionRef("activityCompose")
            library("androidx-compose-bom", "androidx.compose", "compose-bom").versionRef("composeBom")
            library("androidx-compose-ui", "androidx.compose.ui", "ui").withoutVersion()
            library("androidx-compose-ui-graphics", "androidx.compose.ui", "ui-graphics").withoutVersion()
            library("androidx-compose-ui-tooling", "androidx.compose.ui", "ui-tooling").withoutVersion()
            library("androidx-compose-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview").withoutVersion()
            library("androidx-compose-ui-test-manifest", "androidx.compose.ui", "ui-test-manifest").withoutVersion()
            library("androidx-compose-ui-test-junit4", "androidx.compose.ui", "ui-test-junit4").withoutVersion()
            library("androidx-compose-material3", "androidx.compose.material3", "material3").withoutVersion()
            library("androidx-compose-foundation", "androidx.compose.foundation", "foundation").withoutVersion()
            library("androidx-compose-runtime", "androidx.compose.runtime", "runtime").withoutVersion()

            plugin("android-application", "com.android.application").version("8.2.2")
            plugin("kotlin-android", "org.jetbrains.kotlin.android").version("1.9.22")
            plugin("kotlin-compose", "org.jetbrains.kotlin.plugin.compose").version("1.5.8")
        }
    }
}

rootProject.name = "eev3"
include(":app")
