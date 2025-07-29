plugins {
    `java-library`
    `maven-publish`

    alias(libs.plugins.shadow)
}

group = "gg.hoglin"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.okhttp)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
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
        }
    }
}
