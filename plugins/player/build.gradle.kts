plugins { kotlin("jvm") }
dependencies {
    implementation(project(":qq-rich-media"))
    implementation(project(":core")); implementation(project(":nutdraw")); implementation(project(":common")); implementation(project(":http-client"))
    implementation(libs.simbot.core); compileOnly(libs.simbot.component.qq)
    implementation(libs.kotlin.logging.jvm); implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.simbot.component.qq)
    testImplementation(libs.kotlinx.serialization.json)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest {
        attributes("Main-Class" to "cn.luorenmu.plugins.player.PlayerPlugin")
    }
    dependsOn(":qq-rich-media:jar")
    from({ zipTree(project(":qq-rich-media").layout.buildDirectory.file("libs/qq-rich-media-${rootProject.version}.jar")) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
