# Faktory Bot Examples

This directory contains working examples demonstrating all features of Faktory Bot.

## Running Examples

### Prerequisites

1. Database running (PostgreSQL/MySQL/H2)
2. jOOQ code generated
3. Gradle build successful

### Execute Examples

```bash
# Run all examples
./gradlew :examples:test

# Run specific example
./gradlew :examples:test --tests BasicExampleTest
```

## Example Files

### 1. BasicExample.kt
Demonstrates core factory features:
- Factory definition
- build() and create() methods
- buildList() and createList()
- attributes() method
- Attribute overrides

**Key concepts**: Factory DSL, build strategies, basic usage

### 2. SequenceExample.kt
Shows sequence generation patterns:
- Simple sequences
- Email/username sequences
- Formatted sequences (SKU, phone numbers)
- Multiple sequences in one factory
- Sequence reset

**Key concepts**: Unique value generation, sequence management

### 3. TraitExample.kt
Demonstrates trait usage:
- Single trait application
- Multiple trait composition
- Trait with callbacks
- Role/status/flag-based traits
- Trait override precedence

**Key concepts**: Attribute variations, trait composition

### 4. CallbackExample.kt
Shows lifecycle hooks:
- afterBuild callbacks
- beforeCreate callbacks
- afterCreate callbacks
- Creating associations in callbacks
- Callback execution order

**Key concepts**: Lifecycle hooks, association setup

### 5. TransientExample.kt
Demonstrates transient attributes:
- Defining transients
- Accessing transients in callbacks
- Overriding transients
- Controlling associations with transients
- Conditional behavior

**Key concepts**: Temporary attributes, callback configuration

### 6. AdvancedExample.kt
Shows complex scenarios:
- Multi-factory scenarios
- Performance optimization
- Custom sequences
- Association handling (one-to-many, many-to-many)
- Factory composition

**Key concepts**: Complex patterns, optimization, composition

## Usage Patterns

### Quick Start

```kotlin
// 1. Define factory
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

// 2. Build in-memory
val user = dsl.factory<UserRecord>().build()

// 3. Create in database
val savedUser = dsl.factory<UserRecord>().create()

// 4. Create multiple
val users = dsl.factory<UserRecord>().createList(10)
```

### Test Setup

```kotlin
@SpringBootTest
class MyTest {
    @Autowired
    lateinit var dsl: DSLContext

    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()

        // Define factories
        factory<UserRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
        }
    }

    @Test
    fun `my test`() {
        val user = dsl.factory<UserRecord>().create()
        // Test logic
    }
}
```

## Database Schema

Examples assume the following schema:

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    age INT,
    role VARCHAR(50),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE posts (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    content TEXT,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE comments (
    id SERIAL PRIMARY KEY,
    post_id INT REFERENCES posts(id),
    user_id INT REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Documentation

For detailed documentation, see:
- [Getting Started](../docs/usage/01-getting-started.md)
- [Basic Usage](../docs/usage/02-basic-usage.md)
- [Sequences](../docs/usage/03-sequences.md)
- [Traits](../docs/usage/04-traits.md)
- [Callbacks](../docs/usage/05-callbacks.md)
- [Transients](../docs/usage/06-transients.md)
- [Transactions](../docs/usage/07-transactions.md)
- [Advanced Topics](../docs/usage/08-advanced.md)

## Common Patterns

### Create User with Posts

```kotlin
val user = dsl.factory<UserRecord>().create()
val posts = dsl.factory<PostRecord>().createList(5, mapOf(
    "userId" to user.id
))
```

### Use Traits for Variations

```kotlin
factory<UserRecord> {
    name = "User"
    role = "USER"

    trait("admin") {
        attribute("role", "ADMIN")
    }
}

val admin = dsl.factory<UserRecord>().create("admin")
```

### Generate Unique Values

```kotlin
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }
}

val users = dsl.factory<UserRecord>().createList(100)
// All users have unique emails
```

## Tips

1. **Always reset sequences** in `@BeforeEach`
2. **Use transactions** for test isolation
3. **Use createList()** for bulk operations
4. **Define factories once** in test setup
5. **Use traits** for variations
6. **Document transients** for clarity

## Troubleshooting

### Factory Not Found

```kotlin
// Error: Factory not found
val user = dsl.factory<UserRecord>().build()
```

**Solution**: Define factory before use:

```kotlin
factory<UserRecord> {
    name = "User"
}
```

### Sequence Not Resetting

```kotlin
@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()  // Don't forget this!
}
```

### Field Not Found

```kotlin
// Error: Field 'username' not found
factory<UserRecord> {
    username = "user"  // Wrong field name
}
```

**Solution**: Check jOOQ generated Record class for correct field names.

## Next Steps

- Run examples: `./gradlew :examples:test`
- Read documentation in `docs/usage/`
- Explore integration tests in `src/test/kotlin/integration/`
- Check architecture docs in `docs/architecture/`
