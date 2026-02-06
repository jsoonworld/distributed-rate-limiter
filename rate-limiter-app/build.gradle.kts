plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Starter module (core is included transitively)
    implementation(project(":rate-limiter-spring-boot-starter"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Kotlin
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlin.reflect)

    // Monitoring
    implementation(libs.micrometer.prometheus)

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testing.containers)
}

tasks.withType<Test> {
    finalizedBy(tasks.named("jacocoTestReport"))
}
