import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java-library")
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18" apply false
}

subprojects {
    // Apply the java-library plugin to both the api and plugin modules
    apply(plugin = "java-library")

    // Set the group and version for both modules
    group = "ca.xef5000"
    version = "1.0.0"

    // Configure Java settings for both modules
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17)) // Minecraft 1.20.1+ requires Java 17
        //withSourcesJar() // Also generate a -sources.jar file, which is good practice
    }

    // Define the repositories to download dependencies from
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") // For Paper/Spigot API
        maven("https://libraries.minecraft.net") // For Minecraft libraries if needed
    }

    // Common dependencies for both modules (like annotations) can go here
    dependencies {
        // Example: compileOnly("org.jetbrains:annotations:24.0.1")
    }

    // Configure the Java compilation tasks
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters") // Makes parameter reflection easier
    }
}