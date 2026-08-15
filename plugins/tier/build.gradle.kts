plugins { kotlin("jvm") }
dependencies {
    implementation(project(":core")); implementation(project(":nutdraw")); implementation(project(":common")); implementation(project(":http-client"))
    implementation(libs.simbot.core); compileOnly(libs.simbot.component.qq); implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}
kotlin { jvmToolchain(17) }
val pluginJar = tasks.jar
tasks.test {
    useJUnitPlatform()
    dependsOn(pluginJar)
    systemProperty("tier.plugin.jar", pluginJar.flatMap { it.archiveFile }.get().asFile.absolutePath)
}
tasks.jar {
    manifest {
        attributes("Main-Class" to "cn.luorenmu.plugins.tier.TierPlugin")
    }
}
