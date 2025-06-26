plugins {
    kotlin("jvm") version "2.1.10"
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.2"
}

group = "gg.hoglin"
version = "1.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlin.coroutines)

    compileOnly(libs.slf4j)

    testImplementation(libs.logback)
    testImplementation(libs.dotenv)

    implementation(libs.fuel)
    implementation(libs.fuel.coroutines)
    implementation(libs.fuel.serialization)
    implementation(libs.gson)
}

tasks.shadowJar {
    archiveClassifier.set("")

    relocate("com.github.kittinunf.fuel", "gg.hoglin.sdk.shaded.fuel")
    relocate("com.google.gson", "gg.hoglin.sdk.shaded.gson")

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/versions/**")

    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = false
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        javaParameters = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "$group"
            artifactId = "sdk"
            version = "$version"
            artifact(tasks.shadowJar.get().archiveFile)
        }
    }
}

tasks.publish {
    dependsOn(tasks.shadowJar)
}