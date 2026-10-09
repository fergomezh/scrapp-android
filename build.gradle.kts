plugins {
    kotlin("jvm") version "1.9.23" // O la versión que tengas definida
    application
}

group = "com.ctrlcafe.scrapp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.ctrlcafe.scrapp.MainKt")
}

tasks.test {
    useJUnitPlatform()
}