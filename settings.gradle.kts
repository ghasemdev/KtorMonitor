rootProject.name = "KtorMonitor"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
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
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":core:library")
include(":core:library-no-op")
include(":ktor:library-ktor")
include(":ktor:library-ktor-no-op")
include(":ktor:library-attr")
include(":okhttp:library-okhttp")
include(":okhttp:library-okhttp-no-op")
include(":http4k:library-http4k")
include(":http4k:library-http4k-no-op")
include(":sample:shared")
include(":sample:ktor")
include(":sample:okhttp")
include(":sample:http4k")
