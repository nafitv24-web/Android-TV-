plugins {
    kotlin("multiplatform") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
}

kotlin {
    jvm("jvm") {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)

                // Networking & Coroutines
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("org.json:json:20240303")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

                // Video Player for Desktop (VLCJ for HLS/M3U8/MP4 streams)
                implementation("uk.co.caprica:vlcj:4.8.2")
                implementation("uk.co.caprica:vlcj-javafx:1.2.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "NAFITV24"
            packageVersion = "2.6.8"
            description = "NAFI TV 24 - Live Sports & TV Channels for Windows"
            copyright = "© 2026 NAFI TV. All rights reserved."
            vendor = "NAFI TV"

            windows {
                menuGroup = "NAFI TV 24"
                upgradeUuid = "d7c865f1-394e-4f71-a08b-4b13a5e8c109"
            }
        }
    }
}
