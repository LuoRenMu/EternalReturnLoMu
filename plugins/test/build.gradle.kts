plugins { kotlin("jvm") }

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":qq-rich-media"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.simbot.core)
    implementation(libs.kotlin.logging.jvm)
    compileOnly(libs.simbot.component.qq)
    testImplementation(libs.simbot.component.qq)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.sqlite.jdbc)
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest { attributes("Main-Class" to "cn.luorenmu.plugins.test.TestPlugin") }
    // Include only our helper library, not SDK/core classes, so plugin-only hot updates work on older hosts.
    dependsOn(":qq-rich-media:jar")
    from({ zipTree(project(":qq-rich-media").layout.buildDirectory.file("libs/qq-rich-media-${rootProject.version}.jar")) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
