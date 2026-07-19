plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    implementation(libs.tinypinyin)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
