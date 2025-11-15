# Getting Started

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    testImplementation("com.example:faktory-bot:1.0.0")
    testImplementation("org.jooq:jooq:3.18.7")
    testImplementation("org.jooq:jooq-kotlin:3.18.7")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    testImplementation 'com.example:faktory-bot:1.0.0'
    testImplementation 'org.jooq:jooq:3.18.7'
    testImplementation 'org.jooq:jooq-kotlin:3.18.7'
}
```

## Prerequisites

1. **jOOQ Setup**: Faktory Bot requires jOOQ code generation configured
2. **Database**: PostgreSQL, MySQL, or H2
3. **Kotlin**: 1.9.22 or higher

## Basic Setup

### 1. Configure jOOQ Code Generation

```kotlin
jooq {
    configurations {
        create("main") {
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/mydb"
                    user = "user"
                    password = "password"
                }
                generator.apply {
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                    }
                    target.apply {
                        packageName = "com.example.jooq"
                        directory = "src/main/kotlin"
                    }
                }
            }
        }
    }
}
```

### 2. Generate jOOQ Code

```bash
./gradlew jooqCodegen
```

This generates Record classes like `UserRecord`, `PostRecord`, etc.

### 3. Define Your First Factory

```kotlin
import com.example.faktory.dsl.factory
import com.example.jooq.tables.records.UserRecord

factory<UserRecord> {
    name = "John Doe"
    email = "john@example.com"
    age = 30
}
```

### 4. Use the Factory in Tests

```kotlin
import com.example.faktory.dsl.factory
import org.jooq.DSLContext
import org.junit.jupiter.api.Test

class UserTest {
    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `create user`() {
        val user = dsl.factory<UserRecord>().create()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.id).isNotNull()
    }
}
```

## Core Concepts

### Factory Definition

A factory is defined using the `factory<T>` function:

```kotlin
factory<UserRecord> {
    // Attribute definitions
    name = "Default User"
    email = "user@example.com"
    age = 25
}
```

Factories are registered globally and can be accessed via `dsl.factory<T>()`.

### Build Strategies

| Method | Description | Database |
|--------|-------------|----------|
| `build()` | Create in-memory record | No |
| `create()` | Create and persist record | Yes |
| `buildList(n)` | Create n in-memory records | No |
| `createList(n)` | Create and persist n records | Yes |
| `attributes()` | Return evaluated attributes as Map | No |

### DSL Context

Faktory Bot uses jOOQ's `DSLContext` for database operations:

```kotlin
import org.jooq.DSLContext

class MyTest {
    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun test() {
        // Build in-memory
        val user = dsl.factory<UserRecord>().build()

        // Persist to database
        val saved = dsl.factory<UserRecord>().create()
    }
}
```

## Test Setup

### JUnit 5 + Spring Boot

```kotlin
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MyTest {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()

        // Define factories
        factory<UserRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25
        }
    }

    @Test
    fun test() {
        val user = dsl.factory<UserRecord>().create()
        assertThat(user.email).isEqualTo("user1@example.com")
    }
}
```

### Key Setup Steps

1. **Reset Sequences**: Call `GlobalSequenceManager.reset()` in `@BeforeEach`
2. **Clear Registry**: Call `GlobalFactoryRegistry.clear()` to remove old factories
3. **Define Factories**: Define factories in `@BeforeEach` or in a separate setup class

## Next Steps

- Learn about [Basic Usage](02-basic-usage.md) for build/create methods
- See [Sequences](03-sequences.md) for unique value generation
- Check [Traits](04-traits.md) for attribute variations
- Review [examples/BasicExample.kt](../../examples/src/main/kotlin/com/example/faktory/examples/BasicExample.kt)

## Common Issues

### Factory Not Found

```kotlin
// Error: Factory not found for UserRecord
val user = dsl.factory<UserRecord>().build()
```

**Solution**: Define the factory before use:

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"
}
```

### Field Not Found

```kotlin
// Error: Field 'username' not found
factory<UserRecord> {
    username = "user"  // Field doesn't exist in jOOQ generated class
}
```

**Solution**: Use exact field names from jOOQ generated Record class. Check `UserRecord` for available fields.

### Sequence Not Resetting

```kotlin
@Test
fun test1() {
    val user = dsl.factory<UserRecord>().build()
    assertThat(user.email).isEqualTo("user1@example.com")  // Pass
}

@Test
fun test2() {
    val user = dsl.factory<UserRecord>().build()
    assertThat(user.email).isEqualTo("user1@example.com")  // Fail: user2@example.com
}
```

**Solution**: Reset sequences in `@BeforeEach`:

```kotlin
@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()
}
```
