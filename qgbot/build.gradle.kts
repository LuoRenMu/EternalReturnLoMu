plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    rootProject.subprojects
        .filter { it.path.startsWith(":plugins:") && it.path != ":plugins:patch-notes" }
        .forEach { implementation(it) }
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.cio)
    implementation(libs.simbot.core)
    implementation(libs.simbot.component.qq)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:2.3.12")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
application {
    mainClass.set("cn.luorenmu.qqbot.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    systemProperty("lomu.development", "true")
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "cn.luorenmu.qqbot.MainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
