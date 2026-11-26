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
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

// NewPipe v0.26.5 calls the Java 10 URLEncoder/URLDecoder Charset overloads, which do not
// exist on Android API 32 and below. Build the GPL-licensed, source-available patch in-tree.
includeBuild("third_party/newpipe-extractor") {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor")).using(project(":extractor"))
    }
}

rootProject.name = "Myspoti"
include(":app")
include(":api")
include(":catalog")
include(":automix")
include(":extensions")
include(":extensions-cli")
include(":api-spotify")
include(":host-cdp")
include(":host-android")
include(":api-web")
include(":api-ytmusic")
include(":engine")
include(":engine-android")
include(":wear")
include(":pass")
