plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.jsoup.parse)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
