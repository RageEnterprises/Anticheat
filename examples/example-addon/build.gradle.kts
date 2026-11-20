// An example RageAnticheat addon. To start your own, copy this folder out of the repository and
// replace the project(":") dependency with the JitPack one below.
plugins {
    java
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // In your own project: compileOnly("com.github.RageEnterprises:Anticheat:v2.2.0")
    compileOnly(project(":"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
