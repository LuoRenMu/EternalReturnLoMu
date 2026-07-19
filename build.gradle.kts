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

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}