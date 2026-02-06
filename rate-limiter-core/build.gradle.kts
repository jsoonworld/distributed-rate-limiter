plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)

    // Logging (API only)
    compileOnly(libs.slf4j.api)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test> {
    finalizedBy(tasks.named("jacocoTestReport"))
}
