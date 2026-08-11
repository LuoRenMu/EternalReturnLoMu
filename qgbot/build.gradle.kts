plugins {
    kotlin("jvm")
    application
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":core"))
    implementation(project(":plugins:builtin"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.simbot.core)
    implementation(libs.simbot.component.qq)
    implementation(libs.logback.classic)
    testImplementation(kotlin("test"))
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
