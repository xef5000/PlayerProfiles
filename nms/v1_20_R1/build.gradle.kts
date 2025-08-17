plugins {
    // We still need java-library for basic compilation
    id("java-library")
    id("io.papermc.paperweight.userdev")
}


dependencies {
    // This module must implement the interface from your API module.
    implementation(project(":api"))

    paperweight.paperDevBundle("1.20.1-R0.1-SNAPSHOT")
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}