# Faktory Bot Examples

This is an independent Gradle subproject that demonstrates how to use Faktory Bot library in a real application.

## Project Structure

This examples project is set up as a typical user of the Faktory Bot library would configure their project:

```
examples/
├── build.gradle.kts          # Gradle configuration with faktory-bot dependency
├── src/
│   └── main/
│       ├── kotlin/            # Example code
│       └── resources/
│           └── schema.sql     # Database schema for examples
└── README.md
```

## Setup

### 1. Dependencies

The example project depends on the parent Faktory Bot library:

```kotlin
dependencies {
    implementation(project(":"))  // Faktory Bot library

    // Your other dependencies
    implementation("org.jooq:jooq:3.18.7")
    implementation("mysql:mysql-connector-java:8.0.33")
}
```

### 2. jOOQ Code Generation

Configure jOOQ code generation in `build.gradle.kts`:

```kotlin
jooq {
    configurations {
        create("main") {
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "com.mysql.cj.jdbc.Driver"
                    url = "jdbc:mysql://localhost:3306/faktory_test"
                    user = "test"
                    password = "test"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.mysql.MySQLDatabase"
                        inputSchema = "faktory_test"
                    }
                    target.apply {
                        packageName = "com.example.faktory.examples.jooq"
                        directory = "src/main/kotlin"
                    }
                }
            }
        }
    }
}
```

### 3. Generate jOOQ Code

```bash
# From the root project
./gradlew :examples:generateJooq

# Or from within examples/
cd examples
../gradlew generateJooq
```

### 4. Run Examples

```bash
# Run all example tests
./gradlew :examples:test

# Run specific example
./gradlew :examples:test --tests BasicExampleTest
```

## Examples

### Basic Factory Usage

See `BasicExample.kt` for:
- Defining factories with default attributes
- Using `build()` and `create()` methods
- Creating lists of records
- Getting attributes as maps

### Sequences

See `SequenceExample.kt` for:
- Generating unique email addresses
- Formatted sequences with padding
- Multiple independent sequences

### Traits

See `TraitExample.kt` for:
- Defining attribute variations
- Composing multiple traits
- Trait precedence rules

### Callbacks

See `CallbackExample.kt` for:
- `afterBuild`, `beforeCreate`, `afterCreate` hooks
- Creating associations in callbacks
- Validation in `beforeCreate`

### Transients

See `TransientExample.kt` for:
- Controlling callback behavior
- Conditional logic with transients
- Trait-based transient overrides

### Advanced Patterns

See `AdvancedExample.kt` for:
- Custom sequence generators
- Performance optimization with `createList()`
- Factory composition patterns
- Multi-factory test setups

## Key Differences from Library Tests

1. **Independent Project**: This is a separate Gradle subproject that depends on the library
2. **Real-world Setup**: Shows actual jOOQ configuration and database setup
3. **User Perspective**: Demonstrates how end-users would configure and use the library
4. **Isolated jOOQ Generation**: Has its own jOOQ code generation targeting example schema

## Building

```bash
# Build the library first
./gradlew build

# Build examples
./gradlew :examples:build

# Or build everything
./gradlew build
```

## Testing with Database

Examples use Testcontainers for database integration tests:

```kotlin
@Testcontainers
class BasicExample {
    @Container
    val mysql = MySQLContainer("mysql:8.0")
        .withDatabaseName("faktory_test")
        .withUsername("test")
        .withPassword("test")
}
```

## Learn More

- **Library Documentation**: See `../docs/usage/` for comprehensive guides
- **Architecture**: See `../docs/architecture/` for design details
- **Main Tests**: See `../src/test/kotlin/` for library unit tests

## Common Tasks

```bash
# Clean and rebuild
./gradlew :examples:clean :examples:build

# Regenerate jOOQ code
./gradlew :examples:generateJooq

# Run with info logging
./gradlew :examples:test --info

# Run specific test class
./gradlew :examples:test --tests "BasicExampleTest"

# Run specific test method
./gradlew :examples:test --tests "BasicExampleTest.basic factory usage"
```

## Notes

- Examples require Docker for Testcontainers (MySQL)
- jOOQ code is generated into `src/main/kotlin/com/example/faktory/examples/jooq/`
- Generated code is gitignored (regenerate with `generateJooq` task)
- Schema is defined in `src/main/resources/schema.sql`
