import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka)
    alias(libs.plugins.buildkonfig)
}

buildkonfig {
    packageName = "ro.cosminmihu.ktor.monitor.core"

    defaultConfigs {
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            "LIBRARY_VERSION",
            project.version.toString(),
        )
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "ro.cosminmihu.ktor.monitor.ui.resources"
}

dokka {
    moduleName = "Ktor Monitor"
    moduleVersion = project.version.toString()

    dokkaSourceSets.configureEach {
        suppressedFiles.from(
            file("src/commonMain/kotlin/ro/cosminmihu/ktor/monitor/InternalLibraryBridge.kt")
        )

        suppressedFiles.from(
            file("src/commonMain/kotlin/ro/cosminmihu/ktor/monitor/domain/model/NetworkClient.kt")
        )

        perPackageOption {
            matchingRegex.set("ro.cosminmihu.ktor.monitor.ui.resources")
            suppress.set(true)
        }
    }
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
        strictValidation = true
    }

    publicPackages.add("ro.cosminmihu.ktor.monitor")
    ignoredClasses.add("ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi")
    ignoredClasses.add("ro.cosminmihu.ktor.monitor.InternalLibraryBridge")
    ignoredClasses.add("ro.cosminmihu.ktor.monitor.StreamRecorder")
    ignoredClasses.add("ro.cosminmihu.ktor.monitor.StreamRecorder\$Companion")
    ignoredPackages.add("ro.cosminmihu.ktor.monitor.db")
    ignoredPackages.add("ro.cosminmihu.ktor.monitor.domain")
    ignoredPackages.add("ro.cosminmihu.ktor.monitor.ui")
    ignoredPackages.add("org.koin.plugin.hints")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    val artifact = "ktor-monitor-core"
    coordinates(group.toString(), artifact, version.toString())

    pom {
        name.set("Ktor Monitor - Core")
        description.set("""Ktor Client plugin that provides the capability to log HTTP calls for Ktor Monitor.""".trimMargin())
        inceptionYear.set("2025")
        url.set("https://github.com/CosminMihuMDC/KtorMonitor")

        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "Cosmin Mihu"
                name = "Cosmin Mihu"
                url = "https://www.cosminmihu.ro/"
            }
        }

        scm {
            url = "https://github.com/CosminMihuMDC/KtorMonitor.git"
            connection = "scm:git:git://github.com/CosminMihuMDC/KtorMonitor.git"
            developerConnection = "scm:git:git://github.com/CosminMihuMDC/KtorMonitor.git"
        }

        issueManagement {
            system = "GitHub Issues"
            url = "https://github.com/CosminMihuMDC/KtorMonitor/issues"
        }

        ciManagement {
            system = "GitHub Actions"
            url = "https://github.com/CosminMihuMDC/KtorMonitor/actions"
        }

        distributionManagement {
            downloadUrl = "https://github.com/CosminMihuMDC/KtorMonitor/releases"
        }
    }
}

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes") // TODO remove after jetbrains fix
    }

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "ro.cosminmihu.ktor.monitor"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            isStatic = true
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.android.permisssions)
            implementation(libs.sqlite.framework)
            implementation(libs.koin.android)
            implementation(libs.coil.gif)
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive.navigation.suite)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.adaptive)
            implementation(libs.compose.adaptive.layout)
            implementation(libs.compose.adaptive.navigation)
            implementation(libs.compose.adaptive.navigation3)
            implementation(libs.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.room.runtime)
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.coil.svg)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.ksoup)
            implementation(libs.ktor.utils)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        webTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqlite.bundled)
            implementation(libs.slf4j.simple)
        }

        webMain.dependencies {
            implementation(libs.sqlite.web)
            implementation(npm("@sqlite.org/sqlite-wasm", libs.versions.sqlite.wasm.get()))
        }
    }

    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    listOf(
        "kspAndroid",
        "kspJvm",
        "kspIosArm64",
        "kspIosSimulatorArm64",
        "kspJs",
        "kspWasmJs",
    ).forEach { add(it, libs.room.compiler) }
}

// The web tests only exercise the database and do not use Compose UI testing.
tasks.matching { it.name.startsWith("checkComposeUiTestConfigurationFor") }.configureEach {
    enabled = false
}
