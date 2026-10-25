plugins { kotlin("jvm"); kotlin("plugin.serialization") }

// Hot-update support for hosts that predate the shared database adapter.
val bundledSqliteDialect by configurations.creating {
    isTransitive = false
}

dependencies {
    bundledSqliteDialect(libs.ktorm.support.sqlite)
    implementation(libs.ktorm.core)
    implementation(project(":common"))
    implementation(libs.simbot.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging.jvm)
    compileOnly(libs.simbot.component.qq)
    testImplementation(libs.simbot.component.qq)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.postgresql)
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }
tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
    systemProperty("qq.media.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}

tasks.jar {
    dependsOn(":common:jar")
    from({ zipTree(project(":common").layout.buildDirectory.file("libs/common-${rootProject.version}.jar")) }) {
        include("cn/luorenmu/common/database/**")
    }
    from({ bundledSqliteDialect.map { zipTree(it) } }) {
        // Do not expose another ServiceLoader provider to an older host's default dialect detection.
        include("org/ktorm/support/sqlite/**")
    }
}
