plugins { kotlin("jvm") }

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":qq-rich-media"))
    implementation(libs.ktorm.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.simbot.core)
    implementation(libs.kotlin.logging.jvm)
    compileOnly(libs.simbot.component.qq)
    testImplementation(libs.simbot.component.qq)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.postgresql)
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }
tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
    systemProperty("live.plugin.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("test.plugin.jar", project(":plugins:test").layout.buildDirectory.file("libs/test-${project.version}.jar").get().asFile.absolutePath)
    dependsOn(":plugins:test:jar")
}
tasks.jar {
    manifest { attributes("Main-Class" to "cn.luorenmu.plugins.bililive.BiliLivePlugin") }
    dependsOn(":qq-rich-media:jar")
    from({ zipTree(project(":qq-rich-media").layout.buildDirectory.file("libs/qq-rich-media-${rootProject.version}.jar")) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
