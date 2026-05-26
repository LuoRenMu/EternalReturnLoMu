plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":http-client"))
    implementation(libs.kotlin.reflect)
    implementation(libs.simbot.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.freemarker)
    implementation(libs.ktor.serialization.json)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    compileOnly(libs.simbot.component.onebot)
    compileOnly(libs.simbot.component.qq)
    implementation(libs.playwright)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    implementation(libs.caffeine)
    implementation(libs.reflections)
    implementation(libs.mongodb.driver.kotlin.coroutine)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
