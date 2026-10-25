plugins { kotlin("jvm") }

dependencies {
    implementation(libs.ktorm.core)
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":http-client"))
    implementation(project(":qq-rich-media"))
    implementation(libs.simbot.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.ktor.client.cio)
    compileOnly(libs.simbot.component.qq)
    testImplementation(libs.simbot.component.qq)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.postgresql)
    testImplementation(libs.hikari.cp)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest { attributes("Main-Class" to "cn.luorenmu.plugins.grouppet.GroupPetPlugin") }
    dependsOn(":qq-rich-media:jar")
    from({ zipTree(project(":qq-rich-media").layout.buildDirectory.file("libs/qq-rich-media-${rootProject.version}.jar")) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
