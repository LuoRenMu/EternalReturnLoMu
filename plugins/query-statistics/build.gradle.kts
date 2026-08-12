plugins { kotlin("jvm") }

dependencies {
    implementation(project(":core"))
    implementation(project(":nutdraw"))
    implementation(project(":common"))
    implementation(libs.simbot.core)
    compileOnly(libs.simbot.component.qq)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest {
        attributes("Main-Class" to "cn.luorenmu.plugins.querystatistics.QueryStatisticsPlugin")
    }
}
