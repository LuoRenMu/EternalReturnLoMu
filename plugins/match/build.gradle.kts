plugins { kotlin("jvm") }

dependencies {
    implementation(project(":core"))
    implementation(project(":nutdraw"))
    implementation(project(":common"))
    implementation(project(":http-client"))
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}

kotlin { jvmToolchain(17) }
tasks.test {
    useJUnitPlatform()
    systemProperty("match.design.root", rootProject.projectDir.absolutePath)
    systemProperty("user.dir", rootProject.projectDir.absolutePath)
}
tasks.jar {
    manifest { attributes("Main-Class" to "cn.luorenmu.plugins.match.MatchPlugin") }
}
