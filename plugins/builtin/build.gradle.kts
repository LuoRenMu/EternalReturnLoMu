plugins { kotlin("jvm") }
dependencies {
    api(project(":core"))
    implementation(project(":plugins:character")); implementation(project(":plugins:player"))
    implementation(project(":plugins:tier")); implementation(project(":plugins:news"))
    testImplementation(project(":http-client"))
    testImplementation(project(":nutdraw"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
