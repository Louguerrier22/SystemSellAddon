import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "ca.l1cked"
version = "1.0.0"
val pluginName = project.name
val pluginVersion = project.version.toString()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    // 5.4.x includes support for Paper 1.21.8
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly(fileTree("../MayorSystem/build/libs") { include("*.jar") })

    // Shaded into the final jar
    implementation(kotlin("stdlib"))
    implementation("org.incendo:cloud-paper:2.0.0-beta.14")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(
                "version" to pluginVersion,
                "name" to pluginName,
            )
        }
    }

    shadowJar {
        // Keep the shadow jar for local testing, but don't overwrite the thin jar.
        archiveClassifier.set("shadow")
        mergeServiceFiles()
    }
}
