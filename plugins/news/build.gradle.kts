plugins { kotlin("jvm"); kotlin("plugin.serialization") }
dependencies {
    implementation(libs.ktorm.core)
    implementation(project(":qq-rich-media"))
    testImplementation(libs.sqlite.jdbc)
    implementation(project(":core")); implementation(project(":nutdraw")); implementation(project(":common"))
    implementation(project(":ai")); implementation(project(":http-client"))
    implementation(libs.kotlinx.serialization.json); implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.jsoup.parse)
    implementation(libs.simbot.core); compileOnly(libs.simbot.component.qq)
    testImplementation(kotlin("test"))
    testImplementation(libs.simbot.component.qq)
    testRuntimeOnly(libs.skiko.runtime.windows.x64)
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform(); systemProperty("patch.design.root", rootProject.projectDir.absolutePath) }
tasks.jar {
    dependsOn(":qq-rich-media:jar")
    from({ zipTree(project(":qq-rich-media").layout.buildDirectory.file("libs/qq-rich-media-${rootProject.version}.jar")) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    manifest {
        attributes("Main-Class" to "cn.luorenmu.plugins.news.NewsPlugin")
    }
}
