plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("nu.studer.jooq")
    jacoco
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("org.jooq:jooq:3.18.7")
    implementation("org.jooq:jooq-kotlin:3.18.7")

    implementation("com.zaxxer:HikariCP:5.1.0")

    compileOnly("org.junit.jupiter:junit-jupiter-api:5.10.1")

    // Database drivers
    testImplementation("org.postgresql:postgresql:42.7.1")
    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("com.h2database:h2:2.2.224")

    // Test frameworks
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.mockk:mockk:1.13.8")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:mysql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")

    jooqGenerator("org.jooq:jooq-meta-extensions:3.18.7")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)

    // Testcontainers configuration for local development (Colima on macOS)
    // These are optional and only applied if the environment variables are set
    val dockerHost = System.getenv("DOCKER_HOST")
    val dockerSocket = System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")

    if (dockerHost != null) {
        environment("DOCKER_HOST", dockerHost)
    }
    if (dockerSocket != null) {
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", dockerSocket)
    }

    // Disable Ryuk for environments where it's not needed (CI, etc.)
    if (System.getenv("CI") == "true" || System.getenv("TESTCONTAINERS_RYUK_DISABLED") == "true") {
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    }
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { it.file.absolutePath.contains("generated-jooq") }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        sarif.required.set(true)
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
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

jooq {
    version.set("3.18.7")
    configurations {
        create("main") {
            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties.add(
                            org.jooq.meta.jaxb.Property().apply {
                                key = "scripts"
                                value = "src/test/resources/schema.sql"
                            },
                        )
                        properties.add(
                            org.jooq.meta.jaxb.Property().apply {
                                key = "defaultNameCase"
                                value = "lower"
                            },
                        )
                    }
                    target.apply {
                        packageName = "com.example.faktory.test.jooq"
                        directory = "build/generated-jooq"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = true
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir("build/generated-jooq")
        }
    }
}

// Ensure jOOQ code is generated before compilation
tasks.named("compileKotlin") {
    dependsOn("generateJooq")
}

tasks.named("compileTestKotlin") {
    dependsOn("generateJooq")
}
