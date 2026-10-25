plugins { kotlin("jvm"); kotlin("plugin.serialization") }

dependencies {
    implementation(project(":core"))
    implementation(project(":nutdraw"))
    implementation(project(":common"))
    implementation(project(":ai"))
    implementation(project(":http-client"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.jsoup.parse)
    implementation(libs.simbot.core)
    compileOnly(libs.simbot.component.qq)
    testImplementation(kotlin("test"))
    testImplementation(libs.simbot.component.qq)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}

kotlin { jvmToolchain(17) }
tasks.test {
    useJUnitPlatform()
    systemProperty("patch.design.root", rootProject.projectDir.absolutePath)
    dependsOn(tasks.jar)
    systemProperty("patch.plugin.jar", layout.buildDirectory.file("libs/patch-notes-${project.version}.jar").get().asFile.absolutePath)
}
tasks.jar {
    manifest { attributes("Main-Class" to "cn.luorenmu.plugins.patchnotes.PatchNotesPlugin") }
}
