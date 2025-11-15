plugins {
    kotlin("jvm")
    id("nu.studer.jooq") version "8.2"
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Faktory Bot library - depends on parent project
    implementation(project(":"))

    // jOOQ (inherited from library, but explicit for clarity)
    implementation("org.jooq:jooq:3.18.7")
    implementation("org.jooq:jooq-kotlin:3.18.7")

    // Database drivers for examples
    implementation("mysql:mysql-connector-java:8.0.33")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:mysql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")

    // jOOQ code generation - DDL parser
    jooqGenerator("org.jooq:jooq-meta-extensions:3.18.7")
}

kotlin {
    jvmToolchain(17)
}

// jOOQ code generation from DDL files
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
                                value = "src/main/resources/schema.sql"
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
                        packageName = "com.example.faktory.examples.jooq"
                        directory = "src/main/kotlin"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isPojos = false
                        isFluentSetters = true
                    }
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    environment("DOCKER_HOST", "unix:///Users/takuya.kurihara/.colima/default/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/Users/takuya.kurihara/.colima/default/docker.sock")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

// Ensure jOOQ generation runs before compiling Kotlin
tasks.named("compileKotlin") {
    dependsOn(tasks.named("generateJooq"))
}
