import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    jacoco
}

group = "com.jsoonworld"
version = "0.0.1-SNAPSHOT"

// All projects common settings
allprojects {
    repositories {
        mavenCentral()
    }
}

// Sub-projects common settings
subprojects {
    apply(plugin = "jacoco")

    // Kotlin compile options
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            jvmTarget = "21"
        }
    }

    // Test settings
    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // JaCoCo settings
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.11"
    }

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

// Aggregated coverage report
tasks.register<JacocoReport>("jacocoRootReport") {
    dependsOn(subprojects.map { it.tasks.named("test") })

    additionalSourceDirs.setFrom(subprojects.flatMap { subproject ->
        subproject.extensions.findByType<SourceSetContainer>()?.getByName("main")?.allSource?.srcDirs ?: emptySet()
    })

    sourceDirectories.setFrom(subprojects.flatMap { subproject ->
        subproject.extensions.findByType<SourceSetContainer>()?.getByName("main")?.allSource?.srcDirs ?: emptySet()
    })

    classDirectories.setFrom(subprojects.flatMap { subproject ->
        subproject.extensions.findByType<SourceSetContainer>()?.getByName("main")?.output?.classesDirs ?: emptySet()
    })

    executionData.setFrom(subprojects.flatMap { subproject ->
        subproject.tasks.withType<Test>().map { test ->
            test.extensions.getByType<JacocoTaskExtension>().destinationFile
        }
    })

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregate"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregate/jacocoTestReport.xml"))
        csv.required.set(true)
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregate/jacocoTestReport.csv"))
    }
}

// Coverage verification (80% minimum)
tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    dependsOn("jacocoRootReport")

    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    classDirectories.setFrom(subprojects.flatMap { subproject ->
        subproject.extensions.findByType<SourceSetContainer>()?.getByName("main")?.output?.classesDirs ?: emptySet()
    })

    executionData.setFrom(subprojects.flatMap { subproject ->
        subproject.tasks.withType<Test>().map { test ->
            test.extensions.getByType<JacocoTaskExtension>().destinationFile
        }
    })
}
