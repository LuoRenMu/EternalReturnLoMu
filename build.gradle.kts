plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.simbot.core)
    implementation(libs.simbot.component.qq)
    runtimeOnly(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.client.content.negotiation)

 //   implementation(libs.koin.ktor)
    implementation("ch.qos.logback:logback-classic:1.4.11")

    implementation("org.reflections:reflections:0.10.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}