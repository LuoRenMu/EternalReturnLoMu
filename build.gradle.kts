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
val lomuCoreVersion = providers.gradleProperty("lomuCoreVersion").get()
val lomuPluginVersion = providers.gradleProperty("lomuPluginVersion").get()
version = lomuCoreVersion

allprojects {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public/")
        maven(url = "https://jitpack.io")
        mavenCentral()
    }
}

subprojects {
    group = rootProject.group
    version = if (path.startsWith(":plugins:")) lomuPluginVersion else lomuCoreVersion
}

dependencies {
    implementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val commandPluginProjects = subprojects.filter {
    it.path.startsWith(":plugins:")
}

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

tasks.register<Sync>("stageDistributions") {
    group = "distribution"
    description = "Builds qgbot, onebot, and standalone command plugin distributions."
    dependsOn(":qgbot:jar", ":onebot:jar", "stageCommandPlugins", "verifyVersions")

    into(layout.buildDirectory.dir("distributions"))
    into("qgbot") {
        from(project(":qgbot").layout.buildDirectory.file("libs/qgbot-$lomuCoreVersion.jar"))
        into("plugins") { from(layout.buildDirectory.dir("command-plugins")) }
    }
    into("onebot") {
        from(project(":onebot").layout.buildDirectory.file("libs/onebot-$lomuCoreVersion.jar"))
        into("plugins") { from(layout.buildDirectory.dir("command-plugins")) }
    }
    into("plugins") {
        from(layout.buildDirectory.dir("command-plugins"))
    }
}

tasks.register("verifyVersions") {
    group = "verification"
    description = "Verifies core and command plugin project versions."
    doLast {
        check(rootProject.version.toString() == lomuCoreVersion)
        val invalidProjects = subprojects.filter { project ->
            val expected = if (project.path.startsWith(":plugins:")) lomuPluginVersion else lomuCoreVersion
            project.version.toString() != expected
        }
        check(invalidProjects.isEmpty()) {
            "版本配置不一致: ${invalidProjects.joinToString { "${it.path}=${it.version}" }}"
        }
    }
}
