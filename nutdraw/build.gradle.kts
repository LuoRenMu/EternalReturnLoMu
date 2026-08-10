plugins {
    kotlin("jvm")
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":http-client"))
    implementation(libs.skiko.awt)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.skiko.runtime.windows.x64)
    runtimeOnly(libs.skiko.runtime.linux.x64)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
