plugins { kotlin("jvm") }
dependencies {
    implementation(project(":core")); implementation(project(":nutdraw")); implementation(project(":common")); implementation(project(":http-client"))
    implementation(libs.simbot.core); compileOnly(libs.simbot.component.qq)
    implementation(libs.kotlin.logging.jvm); implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest {
        attributes("Main-Class" to "cn.luorenmu.plugins.player.PlayerPlugin")
    }
}
