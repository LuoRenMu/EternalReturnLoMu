plugins { kotlin("jvm") }
dependencies {
    api(project(":core"))
    rootProject.subprojects
        .filter { it.path.startsWith(":plugins:") && it.path != project.path }
        .forEach { implementation(it) }
    testImplementation(project(":http-client"))
    testImplementation(project(":nutdraw"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
