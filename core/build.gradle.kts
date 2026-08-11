plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

group = "cn.luorenmu"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(project(":nutdraw"))
    implementation(libs.skiko.awt)
    implementation(project(":common"))
    implementation(project(":http-client"))
    implementation(project(":ai"))
    implementation(libs.kotlin.reflect)
    api(libs.simbot.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.freemarker)
    implementation(libs.ktor.serialization.json)
    api("io.insert-koin:koin-core:3.5.5")
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    compileOnly(libs.simbot.component.onebot)
    compileOnly(libs.simbot.component.qq)

    implementation(libs.kotlin.logging.jvm)
    implementation(libs.caffeine)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.postgresql)
    implementation(libs.postgresql)
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikari.cp)
    testImplementation(kotlin("test"))
    // QGMarkdown 等 QQ 组件类型在核心模块为 compileOnly，测试运行期需要显式加入
    testImplementation(libs.simbot.component.qq)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
