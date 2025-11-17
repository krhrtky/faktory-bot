# Faktory Bot - Build Instructions

## Prerequisites

- JDK 17 or higher
- Gradle 8.5 or higher (or use Gradle wrapper)

## Project Structure

```
faktory-bot/
├── faktory-core/       # Core runtime library with Factory DSL
└── examples/           # Usage examples
```

## Building the Project

### Option 1: Using Gradle Wrapper (Recommended)

If you don't have Gradle wrapper yet, initialize it:

```bash
gradle wrapper --gradle-version 8.5
```

Then build:

```bash
./gradlew build
```

### Option 2: Using System Gradle

```bash
gradle build
```

## Build Tasks

### Build All Modules

```bash
./gradlew build
```

### Build Specific Module

```bash
./gradlew :faktory-core:build
./gradlew :examples:build
```

### Generate jOOQ Code

For faktory-core (test fixtures):

```bash
./gradlew :faktory-core:generateJooq
```

For examples:

```bash
./gradlew :examples:generateJooq
```

### Run Tests

```bash
./gradlew test
```

With coverage report:

```bash
./gradlew test jacocoTestReport
```

### Code Quality Checks

```bash
# Kotlin linting
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat

# Static analysis
./gradlew detekt

# All quality checks
./gradlew checkQuality
```

## Working with Examples

### Generate jOOQ Code from Schema

```bash
cd examples
./gradlew generateJooq
```

This will generate jOOQ Record classes from `examples/src/main/resources/schema.sql`.

### Use Factory DSL

Define factories using the DSL:

```kotlin
import com.example.faktory.dsl.factory
import com.example.faktory.examples.jooq.tables.records.UsersRecord

// Define factory
factory<UsersRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25

    trait("admin") {
        role = "admin"
    }

    afterCreate { user ->
        println("Created user: ${user.name}")
    }
}
```

Use the factory to build or create records:

```kotlin
import com.example.faktory.builder.build
import com.example.faktory.builder.create

// Build (in-memory)
val user1 = build<UsersRecord> {
    name = "Alice"
    email = "alice@example.com"
}

// Create (with DB persistence)
val user2 = create<UsersRecord>(dsl) {
    name = "Bob"
    email = "bob@example.com"
}

// With traits
val admin = create<UsersRecord>(dsl, traits = listOf("admin")) {
    name = "Admin"
    email = "admin@example.com"
}
```

## Build Output

Generated files are located in:

- **jOOQ Generated Code**: `build/generated-jooq/`
- **Compiled Classes**: `build/classes/`
- **Test Reports**: `build/reports/tests/`
- **Coverage Reports**: `build/reports/jacoco/`

## Troubleshooting

### Missing jOOQ Generated Code

Ensure jOOQ generation runs before compilation:

```bash
./gradlew :examples:clean :examples:generateJooq :examples:compileKotlin
```

### Compilation Errors

Clean and rebuild:

```bash
./gradlew clean build
```

### Test Failures (Testcontainers)

Ensure Docker is running and accessible:

```bash
docker ps
```

For Colima users, check socket path in `build.gradle.kts`:

```kotlin
tasks.test {
    environment("DOCKER_HOST", "unix:///Users/$USER/.colima/default/docker.sock")
}
```

## Next Steps

1. Review existing examples in `faktory-core/src/test/kotlin/com/example/faktory/examples/`
2. Write tests using the Factory DSL
3. Explore advanced features: traits, callbacks, associations, transactions

## Documentation

- [Factory DSL Guide](docs/features/factory-dsl.md)
- [Core Interfaces](docs/architecture/core-interfaces.md)
