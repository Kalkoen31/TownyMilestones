// Gradle Kotlin DSL build, following the Paper docs' project setup page.

plugins {
    java
    // Adds the runServer task: starts a throwaway Paper test server with this plugin loaded.
    // 3.1.0 is the latest version on the Gradle plugin portal (released August 2026). Version 2.3.1 fails
    // on Gradle 9.8 with "downloadPlugins.elements is missing an input or output annotation".
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.kalkoen31"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    // Towny's repository ("Glare's repo" in the TownyAPI wiki page).
    maven("https://repo.glaremasters.me/repository/towny/") { name = "glaremasters" }
    // PlaceholderAPI's repository (HelpChat).
    maven("https://repo.helpch.at/releases/") { name = "helpchat" }
}

dependencies {
    // Both are provided by the server at runtime, so compileOnly: they are never bundled in the jar.
    // Exact pins for reproducible builds: the Paper build the server runs, and the Towny release it runs.
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
    compileOnly("me.clip:placeholderapi:2.12.3")

    // Unit tests (src/test/java). The Paper API is needed because the code under test uses its types.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
}

java {
    // The Paper API jars are Java 25 class files, so Java 25 is both the compiling JDK and the output target.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.runServer {
    // The Minecraft version of the test server. The server files go in the run/ folder.
    minecraftVersion("26.2")
}
