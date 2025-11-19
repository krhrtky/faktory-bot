plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.4" apply false
    id("nu.studer.jooq") version "8.2" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

group = "io.github.krhrtky"
version = "0.2.0"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }
}
