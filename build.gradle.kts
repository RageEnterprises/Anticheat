plugins {
    java
}

group = "dev.rage"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Compiled against the oldest supported API so the jar loads on Paper 1.20 and newer.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // Discord bot. Downloaded by Paper at runtime through plugin.yml libraries, so not shaded.
    compileOnly("net.dv8tion:JDA:5.2.1") {
        exclude(module = "opus-java")
    }

    // Paper provides Gson at runtime; the tests need their own copy.
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
