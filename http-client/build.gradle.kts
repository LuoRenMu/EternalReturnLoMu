plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(project(":common"))
    implementation(libs.kotlin.reflect)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.caffeine)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
