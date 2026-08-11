plugins {
    kotlin("jvm")
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(project(":core"))
    implementation(project(":plugins:builtin"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.netty)
    implementation(libs.simbot.core)
    implementation(libs.simbot.component.onebot)
    implementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "cn.luorenmu.onebot.OneBotMainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
