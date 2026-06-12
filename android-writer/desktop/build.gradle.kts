import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "com.inkblue.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "InkBlueWriter"
            // macOS requires the leading version number to be > 0;
            // 1.6.x tracks app version 0.6.x.
            packageVersion = "1.6.1"
            description = "墨蓝写作 InkBlue Writer 桌面版"
            macOS {
                dockName = "墨蓝写作"
            }
        }
    }
}
