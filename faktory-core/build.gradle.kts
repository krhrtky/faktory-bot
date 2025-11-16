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

    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:mysql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")

    jooqGenerator("org.jooq:jooq-meta-extensions:3.18.7")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    environment("DOCKER_HOST", "unix:///Users/takuya.kurihara/.colima/default/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/Users/takuya.kurihara/.colima/default/docker.sock")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
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
                            }
                        )
                        properties.add(
                            org.jooq.meta.jaxb.Property().apply {
                                key = "defaultNameCase"
                                value = "lower"
                            }
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
