// This module should be a simple library with no executable main class
plugins {
    `java-library`
}

dependencies {
    // The API module needs to know about the Minecraft/Bukkit API.
    // We use `compileOnly` because the server provides these classes at runtime.
    // We DO NOT want to bundle the server JAR into our API jar.
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
}