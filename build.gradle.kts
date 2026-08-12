plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}


tasks.jar {
    manifest {
        attributes["Main-Class"] = "cn.luorenmu.MainApplication"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
group = "cn.luorenmu"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public/")
        maven(url = "https://jitpack.io")
        mavenCentral()
    }
}

dependencies {
    implementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val commandPluginProjects = listOf(
    project(":plugins:character"),
    project(":plugins:player"),
    project(":plugins:tier"),
    project(":plugins:news"),
    project(":plugins:query-statistics"),
)

tasks.register<Sync>("stageCommandPlugins") {
    group = "distribution"
    description = "Builds standalone hot-reloadable command plugin jars."
    commandPluginProjects.forEach { pluginProject ->
        val pluginJar = pluginProject.tasks.named("jar")
        dependsOn(pluginJar)
        from(pluginJar)
    }
    into(layout.buildDirectory.dir("command-plugins"))
}
