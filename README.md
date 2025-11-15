# Faktory Bot

Type-safe test data factory library for jOOQ and Kotlin, inspired by Ruby's Factory Bot.

## Features

- 🔒 **Type-safe**: Full compile-time type checking with Kotlin and jOOQ
- 🚀 **Fast**: Batch insert optimization with `createList()`
- 🧩 **Flexible**: Traits, callbacks, and factory inheritance
- 🔄 **Test isolation**: Automatic transaction rollback support
- 📝 **Declarative DSL**: Clean and readable factory definitions

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    testImplementation("com.example:faktory-bot:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    testImplementation 'com.example:faktory-bot:0.1.0'
}
```

## Quick Start

### 1. Define a Factory

```kotlin
import com.example.faktory.dsl.factory

factory<UsersRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}
```

### 2. Use in Tests

```kotlin
@Test
fun `user can be created`() {
    val user = dsl.factory<UsersRecord>().create()

    assertThat(user.name).isEqualTo("Default User")
    assertThat(user.email).matches("user\\d+@example.com")
    assertThat(user.age).isEqualTo(25)
}
```

## Core Concepts

### Build Strategies

```kotlin
// Build only (no database)
val user = dsl.factory<UsersRecord>().build()

// Create in database
val user = dsl.factory<UsersRecord>().create()

// Build multiple records
val users = dsl.factory<UsersRecord>().buildList(10)

// Create multiple records (optimized batch insert)
val users = dsl.factory<UsersRecord>().createList(10)

// Get attributes as Map
val attrs = dsl.factory<UsersRecord>().attributes()
```

### Sequences

Generate unique values automatically:

```kotlin
factory<UsersRecord> {
    email = sequence { n -> "user${n}@example.com" }
}

// Generates: user1@example.com, user2@example.com, ...
```

### Traits

Reusable attribute variations:

```kotlin
factory<UsersRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25

    trait("admin") {
        role = "ADMIN"
    }

    trait("inactive") {
        isActive = false
    }
}

// Use traits
val admin = dsl.factory<UsersRecord>().create("admin")
val inactiveAdmin = dsl.factory<UsersRecord>().create("admin", "inactive")
```

### Associations

Automatically create related records:

```kotlin
factory<PostsRecord> {
    title = "Default Post"
    content = "Content"
    // userId will be automatically set from created user
}

val post = dsl.factory<PostsRecord>().create()
```

### Transients

Pass non-database values to callbacks:

```kotlin
factory<UsersRecord> {
    name = "User"
    email = "user@example.com"

    transient {
        set("postsCount", 5)
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("postsCount") as? Int
        repeat(count ?: 0) {
            PostsFactory.create(userId = user.id)
        }
    }
}
```

### Callbacks

Execute code at specific lifecycle points:

```kotlin
factory<UsersRecord> {
    name = "User"

    afterBuild { user, _ ->
        println("Built user: ${user.name}")
    }

    beforeCreate { user, _ ->
        user.createdAt = Timestamp.from(Instant.now())
    }

    afterCreate { user, _ ->
        AuditLogFactory.create(targetId = user.id)
    }
}
```

### Transactions

Automatic rollback for test isolation:

```kotlin
import com.example.faktory.transaction.withFactoryTransaction

@Test
fun `test with automatic rollback`() = withFactoryTransaction {
    val user = dsl.factory<UsersRecord>().create()
    val posts = dsl.factory<PostsRecord>().createList(10)

    // All changes automatically rolled back after test
}
```

## Advanced Usage

### Factory Inheritance

```kotlin
factory<UsersRecord>("base_user") {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

factory<UsersRecord>("admin_user", parent = "base_user") {
    role = "ADMIN"
}
```

### Override Attributes

```kotlin
val user = dsl.factory<UsersRecord>().create(
    mapOf(
        "name" to "Custom Name",
        "age" to 30
    )
)
```

### JUnit 5 Integration

```kotlin
import com.example.faktory.transaction.FactoryTransactionExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FactoryTransactionExtension::class)
class UserServiceTest {
    @Test
    fun testUser() {
        val user = dsl.factory<UsersRecord>().create()
        // Automatically rolled back after test
    }
}
```

## Requirements

- Kotlin 1.9+
- jOOQ 3.18+
- JDK 17+

## Documentation

- [User Guide](docs/README.md)
- [API Documentation](https://example.github.io/faktory-bot/)
- [Examples](examples/)

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

Inspired by [Factory Bot](https://github.com/thoughtbot/factory_bot) for Ruby.

## Support

- GitHub Issues: https://github.com/example/faktory-bot/issues
- Discussions: https://github.com/example/faktory-bot/discussions
