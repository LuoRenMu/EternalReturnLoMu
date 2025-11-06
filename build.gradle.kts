plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.1.20"

}


tasks.jar {
    manifest {
        attributes["Main-Class"] = "cn.luorenmu.DownloadMainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.client.content.negotiation)
    implementation("com.microsoft.playwright:playwright:1.42.0")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation(libs.ktor.freemarker)
    implementation(libs.koin.ktor)
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
    implementation("org.reflections:reflections:0.10.2")
    implementation("io.ktor:ktor-server-freemarker:2.3.12")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.6")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}