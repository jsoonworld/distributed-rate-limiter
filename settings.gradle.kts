rootProject.name = "distributed-rate-limiter"

// Include modules
include(
    "rate-limiter-core",
    "rate-limiter-spring-boot-starter",
    "rate-limiter-app"
)

// Gradle plugin repositories
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
