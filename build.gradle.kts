import java.net.URI

plugins {
    `java-library`
    `maven-publish`

    alias(libs.plugins.shadow)
}

group = "gg.hoglin"
version = "1.0.5"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    implementation(libs.unirest.core)
    implementation(libs.unirest.gson)
    implementation(libs.slf4j)
    compileOnly(libs.lombok)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.autoservice)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.autoservice)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("tag", "apiNote:a:API Note:")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "$group"
            artifactId = "sdk"
            version = "$version"
            artifact(tasks.shadowJar.get().archiveFile)
            artifact(tasks.named("sourcesJar").get())
            artifact(tasks.named("javadocJar").get())
        }
    }

    repositories {
        maven {
            name = "Waypoint-Studios"
            url = URI("https://maven.waypointstudios.com/releases")
            credentials {
                username = findProperty("repo.waypoint.username") as String? ?: System.getenv("REPO_USER")
                password = findProperty("repo.waypoint.password") as String? ?: System.getenv("REPO_PASS")
            }
        }
    }
}
