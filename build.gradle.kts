plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "com.aiagent"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:0.1.0")
    implementation("com.google.genai:google-genai:1.2.0")
    testImplementation(kotlin("test"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")

    // optional extensions for running animations with coroutines
    implementation("com.github.ajalt.mordant:mordant-coroutines:3.0.2")

    // optional widget for rendering Markdown
    implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}