import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    // We apply the shadow plugin here, which is used to bundle dependencies into the final JAR
    id("com.gradleup.shadow")
}

// This block tells Gradle to replace placeholders like ${version} in your plugin.yml
tasks.processResources {
    filesMatching("plugin.yml") {
        expand(project.properties)
    }
}

dependencies {
    // --- Project Dependencies ---

    // 1. This is the crucial line that makes the plugin module depend on the API module.
    //    It allows you to use your own interfaces like CharacterProfile and CharacterManager.
    implementation(project(":api"))

    // NMS
    implementation(project(":nms:v1_20_R1"))
    implementation(project(":nms:v1_20_R2"))
    implementation(project(":nms:v1_20_R3"))
    implementation(project(":nms:v1_20_R4"))
    implementation(project(":nms:v1_21_R1"))
    implementation(project(":nms:v1_21_R2"))


    // --- Compile-Only Dependencies (provided by server or other plugins) ---

    // The plugin needs the Paper API to run, but the server provides it.
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")

    // For the LuckPerms context calculator, you need the LuckPerms API.
    // The LuckPerms plugin will provide this on the server.
    compileOnly("net.luckperms:api:5.4")

    implementation("com.google.code.gson:gson:2.10.1")

    // --- Implementation Dependencies (will be bundled into your JAR) ---

    // This is the Java client for the Mineskin API.
    // We use `implementation` so the shadow plugin bundles it into your plugin's JAR file.
    //implementation("org.mineskin:java-client:1.2.0")
}

tasks.jar {
    enabled = false
}

// Configure the ShadowJar task to create the final, runnable plugin JAR
tasks.shadowJar {
    archiveBaseName.set("PlayerProfiles")

    // This is a BEST PRACTICE. It renames the packages of the libraries you bundle.
    // It prevents your plugin from crashing if another plugin on the server uses a
    // different version of the Mineskin client.
    relocate("org.mineskin", "ca.xef5000.playerprofiles.lib.mineskin")
    relocate("com.google.gson", "ca.xef5000.playerprofiles.lib.gson")

    // Specify the output file name for the final JAR.
    archiveClassifier.set("") // This removes the "-all" suffix, creating a clean "PlayerCharacters-1.0.0-SNAPSHOT.jar"
}

// Make the standard `build` task also run the `shadowJar` task
tasks.build {
    dependsOn(tasks.shadowJar)
}