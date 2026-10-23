plugins { kotlin("jvm"); kotlin("plugin.serialization") }

dependencies {
    implementation(project(":common"))
    implementation(libs.simbot.core)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.simbot.component.qq)
    testImplementation(libs.simbot.component.qq)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
