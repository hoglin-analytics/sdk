<div align="center">
  <img src="logo.svg" width="500">
  
  <br/>
  
  # Hoglin SDK

  <a href='https://hoglin.gg'>Hoglin</a>
  •
  <a href="https://docs.hoglin.gg/developer-sdk/installation">Docs</a>
  •
  <a href='https://discord.gg/hoglin'>Support Discord</a>
  
</div>

<br/>

This is our public Java (and Kotlin) SDK for interactions with [Hoglin](https://hoglin.gg). Our SDK aims to be lightweight yet powerful and platform-agnostic, allowing you to start tracking custom Hoglin analytics with minimal lines of code.

<br/>

## Getting started - Installation

Gradle `build.gradle.kts`
```kts
repositories {
    maven {
      url = uri("https://maven.waypointstudios.com/releases")
    }
}

dependencies {
    implementation("gg.hoglin:sdk:1.2.0")
}
```

Maven `pom.xml`
```xml
<repositories>
    <repository>
        <id>waypoint-studios</id>
        <name>Waypoint Studios</name>
        <url>https://maven.waypointstudios.com/releases</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>gg.hoglin</groupId>
        <artifactId>sdk</artifactId>
        <version>1.2.0</version>
    </dependency>
</dependencies>
```

<br/>

Check out our [full documentation](https://docs.hoglin.gg/developer-sdk/installation) for detailed usage of the SDK.

<br/>

## Building

To clone the repository, use `git clone https://github.com/WaypointStudios/hoglin-sdk.git`

To then build the project, use `./gradlew clean build`
