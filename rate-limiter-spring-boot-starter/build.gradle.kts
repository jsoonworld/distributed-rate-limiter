plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.spring.dependency.management)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    // Core module
    api(project(":rate-limiter-core"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation(libs.bundles.coroutines)

    // Micrometer
    implementation(libs.micrometer.core)

    // Configuration Processor
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testing.containers)
    testImplementation(libs.micrometer.core)
}

tasks.withType<Test> {
    finalizedBy(tasks.named("jacocoTestReport"))
}
