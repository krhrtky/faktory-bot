plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    jacoco
    `maven-publish`
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("org.jooq:jooq:3.18.7")
    implementation("org.jooq:jooq-kotlin:3.18.7")

    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/detekt.yml")
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude("**/jooq/generated/**")
                }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.register("checkQuality") {
    dependsOn("ktlintCheck", "detekt", "test", "jacocoTestCoverageVerification")
}
