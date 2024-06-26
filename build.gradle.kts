import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = properties["groupId"]!!
version = properties["version"]!!

val shade: Configuration = configurations.register("shade") {
    configurations.implementation.get().extendsFrom(this)
}.get()

repositories {
    mavenCentral()
    maven(url = "https://maven.fabricmc.net")
}

dependencies {
    configurations.implementation.get().isCanBeResolved = true

    val loaderVersion: String by project
    val mixinVersion: String by project
    val mixinExtrasVersion: String by project

    shade(group = "net.fabricmc", name = "fabric-loader", version = loaderVersion)

    shade(group = "net.fabricmc", name = "access-widener", version = "2.1.0")
    implementation(group = "net.fabricmc", name = "sponge-mixin", version = mixinVersion) {
        exclude(module = "launchwrapper")
        exclude(module = "guava")
        exclude(module = "gson")
    }
    shade(group = "com.google.guava", name = "guava", version = "33.1.0-jre")
    shade(group = "com.google.code.gson", name = "gson", version = "2.10.1")
}

java {
    withSourcesJar()
}

tasks {
    named<Jar>("jar") {
        dependsOn(shadowJar)
        dependsOn("generateOtherFiles")

        manifest {
            attributes(
                mapOf(
                    "Class-Path" to configurations.runtimeClasspath.get().joinToString(" ") { it.getName() },
                    "Specification-Version" to 8.0,
                    "Multi-Release" to true,
                    "Main-Class" to "${project.properties["groupId"]!!}.provider.Main"
                )
            )
        }
    }
    named<ShadowJar>("shadowJar") {
        configurations = listOf(shade)
        archiveBaseName.set("GameProvider")
        archiveVersion.set("")
        archiveClassifier.set("")
    }
    register("generateOtherFiles") {
        val outputDir = layout.buildDirectory.dir("libs").get().asFile
        val libraryDir = File(outputDir, "libraries")

        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }

        val deps = configurations.implementation.get()
            .filter { shade.all { shaded -> shaded.path != it.path } }

        deps.map { Pair(it, File(libraryDir, it.name)) }.forEach { (file, dest) -> file.copyTo(dest, true) }

        val cp = "LunarCore.jar;GameProvider.jar;${deps.joinToString(";") { "libraries/" + it.name }}"

        val argsFile = File(outputDir, "args.txt")
        argsFile.writeText("-cp $cp\n${project.group}.provider.Main")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            shadow.component(this)
        }
    }
}