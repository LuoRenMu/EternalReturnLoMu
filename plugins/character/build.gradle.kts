plugins { kotlin("jvm") }
dependencies {
    implementation(project(":qq-rich-media"))
    implementation(project(":core")); implementation(project(":nutdraw")); implementation(project(":common")); implementation(project(":http-client"))
    implementation(libs.simbot.core); implementation(libs.kotlinx.coroutines.core); implementation(libs.kotlin.logging.jvm)
    compileOnly(libs.ktor.client.cio)
    compileOnly(libs.simbot.component.qq)
    testImplementation(libs.simbot.component.qq)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
tasks.jar {
    dependsOn(":qq-rich-media:jar")
    from({ zipTree(project(":qq-rich-media").layout.buildDirectory.file("libs/qq-rich-media-${rootProject.version}.jar")) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    manifest {
        attributes("Main-Class" to "cn.luorenmu.plugins.character.CharacterPlugin")
    }
}
