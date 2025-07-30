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
    implementation(libs.gson)
    implementation(libs.unirest.core)
    implementation(libs.unirest.gson)
    compileOnly(libs.lombok)
    compileOnly(libs.jetbrains.annotations)
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
