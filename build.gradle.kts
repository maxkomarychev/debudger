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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}