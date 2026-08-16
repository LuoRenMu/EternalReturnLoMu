plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    implementation(project(":common"))
    api(libs.skiko.awt)
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
