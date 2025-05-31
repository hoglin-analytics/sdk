plugins {
    kotlin("jvm") version "2.1.10"
    id("maven-publish")
}

group = "gg.hoglin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlin.coroutines)

    implementation(libs.slf4j)

    testImplementation(libs.logback)
    testImplementation(libs.dotenv)

    implementation(libs.fuel)
    implementation(libs.fuel.coroutines)
    implementation(libs.fuel.serialization)
    implementation(libs.gson)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(19)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "$group"
            artifactId = "sdk"
            version = "$version"
        }
    }
}