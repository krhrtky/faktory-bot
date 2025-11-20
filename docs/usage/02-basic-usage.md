# Basic Usage

## Build Strategies Overview

Faktory Bot provides five main methods for working with factories:

| Method | Returns | Database | Use Case |
|--------|---------|----------|----------|
| `build()` | Single record | No | Unit tests, in-memory validation |
| `create()` | Single record | Yes | Integration tests, DB-dependent logic |
| `buildList(n)` | List of records | No | Bulk in-memory data |
| `createList(n)` | List of records | Yes | Bulk DB insert (simple loop) |
| `attributes()` | Map<String, Any?> | No | API request payloads |

## build() - In-Memory Record

Creates a jOOQ Record without database persistence.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "John Doe"
    email = "john@example.com"
    age = 30
}

val user = dsl.factory<UserRecord>().build()

assertThat(user.name).isEqualTo("John Doe")
assertThat(user.email).isEqualTo("john@example.com")
assertThat(user.id).isNull()  // No DB insert
```

### With Overrides

```kotlin
val user = dsl.factory<UserRecord>().build(mapOf(
    "name" to "Jane Smith",
    "age" to 25
))

assertThat(user.name).isEqualTo("Jane Smith")
assertThat(user.email).isEqualTo("john@example.com")  // From factory
assertThat(user.age).isEqualTo(25)
```

### When to Use

- Unit tests that don't need database
- Validating object creation logic
- Testing serialization/deserialization
- Fast test execution

## create() - Persisted Record

Creates and persists a jOOQ Record to the database.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "John Doe"
    email = "john@example.com"
    age = 30
}

val user = dsl.factory<UserRecord>().create()

assertThat(user.id).isNotNull()  // Auto-generated primary key
assertThat(user.name).isEqualTo("John Doe")

// Verify in database
val found = dsl.selectFrom(USERS)
    .where(USERS.ID.eq(user.id))
    .fetchOne()

assertThat(found).isNotNull()
```

### With Overrides

```kotlin
val user = dsl.factory<UserRecord>().create(mapOf(
    "email" to "custom@example.com"
))

assertThat(user.email).isEqualTo("custom@example.com")
assertThat(user.id).isNotNull()
```

### When to Use

- Integration tests
- Testing database constraints
- Testing triggers and stored procedures
- Testing relationships and foreign keys

## buildList() - Multiple In-Memory Records

Creates multiple in-memory records.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

val users = dsl.factory<UserRecord>().buildList(5)

assertThat(users).hasSize(5)
assertThat(users[0].email).isEqualTo("user1@example.com")
assertThat(users[4].email).isEqualTo("user5@example.com")
assertThat(users.all { it.id == null }).isTrue()
```

### With Overrides

```kotlin
val users = dsl.factory<UserRecord>().buildList(3, mapOf(
    "age" to 30
))

assertThat(users).hasSize(3)
assertThat(users.all { it.age == 30 }).isTrue()
```

### When to Use

- Testing collection processing
- Testing sorting/filtering logic
- Performance testing with large datasets
- In-memory data manipulation

## createList() - Multiple Persisted Records

Creates and persists multiple records (currently uses individual INSERTs, batch optimization planned).

### Basic Example

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

val users = dsl.factory<UserRecord>().createList(10)

assertThat(users).hasSize(10)
assertThat(users.all { it.id != null }).isTrue()

// Verify in database
val count = dsl.selectCount()
    .from(USERS)
    .fetchOne(0, Int::class.java)

assertThat(count).isEqualTo(10)
```

### Current Implementation

```kotlin
// 現在の実装: 両方とも個別INSERT
repeat(10) {
    dsl.factory<UserRecord>().create()
}

// createList()も内部ではループ処理
// (将来的にバッチINSERTで最適化予定)
dsl.factory<UserRecord>().createList(10)
```

### With Overrides

```kotlin
val users = dsl.factory<UserRecord>().createList(5, mapOf(
    "name" to "Bulk User"
))

assertThat(users).hasSize(5)
assertThat(users.all { it.name == "Bulk User" }).isTrue()
```

### When to Use

- Seeding test databases
- Performance testing with large datasets
- Testing pagination
- Testing batch operations

## attributes() - Attribute Map

Returns evaluated attributes as a Map without creating a Record.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"
    age = 25
}

val attrs = dsl.factory<UserRecord>().attributes()

assertThat(attrs).containsEntry("name", "User")
assertThat(attrs).containsEntry("email", "user@example.com")
assertThat(attrs).containsEntry("age", 25)
```

### With Sequences

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

val attrs1 = dsl.factory<UserRecord>().attributes()
val attrs2 = dsl.factory<UserRecord>().attributes()

assertThat(attrs1["email"]).isEqualTo("user1@example.com")
assertThat(attrs2["email"]).isEqualTo("user2@example.com")
```

### With Overrides

```kotlin
val attrs = dsl.factory<UserRecord>().attributes(mapOf(
    "name" to "Custom User"
))

assertThat(attrs["name"]).isEqualTo("Custom User")
assertThat(attrs["email"]).isEqualTo("user@example.com")
```

### When to Use

- Testing REST API request payloads
- Testing serialization logic
- Generating JSON/XML test data
- Testing validation without DB

### Example: API Testing

```kotlin
@Test
fun `POST /users creates user`() {
    val payload = dsl.factory<UserRecord>().attributes(mapOf(
        "email" to "new@example.com"
    ))

    val response = mockMvc.perform(
        post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload))
    )

    response.andExpect(status().isCreated)
}
```

## Attribute Override Strategies

### Static Override

```kotlin
val user = dsl.factory<UserRecord>().build(mapOf(
    "name" to "Custom Name"
))
```

### Dynamic Override

```kotlin
val user = dsl.factory<UserRecord>().build(mapOf(
    "createdAt" to LocalDateTime.now()
))
```

### Partial Override

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"
    age = 25
}

val user = dsl.factory<UserRecord>().build(mapOf(
    "age" to 30  // Only override age
))

assertThat(user.name).isEqualTo("User")  // From factory
assertThat(user.age).isEqualTo(30)        // Overridden
```

## Method Chaining Not Supported

Faktory Bot does not support method chaining for overrides:

```kotlin
// ❌ NOT SUPPORTED
val user = dsl.factory<UserRecord>()
    .withName("Custom")
    .withAge(30)
    .build()

// ✅ Use map overrides instead
val user = dsl.factory<UserRecord>().build(mapOf(
    "name" to "Custom",
    "age" to 30
))
```

## Best Practices

### 1. Use build() for Fast Tests

```kotlin
@Test
fun `user has full name`() {
    val user = dsl.factory<UserRecord>().build(mapOf(
        "firstName" to "John",
        "lastName" to "Doe"
    ))

    assertThat(user.fullName).isEqualTo("John Doe")
}
```

### 2. Use create() for Integration Tests

```kotlin
@Test
fun `database constraint prevents duplicate email`() {
    dsl.factory<UserRecord>().create(mapOf(
        "email" to "user@example.com"
    ))

    assertThrows<DataIntegrityViolationException> {
        dsl.factory<UserRecord>().create(mapOf(
            "email" to "user@example.com"
        ))
    }
}
```

### 3. Use createList() for Bulk Data

```kotlin
@Test
fun `pagination returns 10 users per page`() {
    dsl.factory<UserRecord>().createList(25)

    val page1 = userService.findAll(PageRequest.of(0, 10))
    assertThat(page1).hasSize(10)
}
```

### 4. Use attributes() for API Tests

```kotlin
@Test
fun `API validates required fields`() {
    val invalidPayload = dsl.factory<UserRecord>().attributes(mapOf(
        "email" to null  // Invalid
    ))

    val response = mockMvc.perform(
        post("/users").content(json(invalidPayload))
    )

    response.andExpect(status().isBadRequest)
}
```

## Common Patterns

### Setup Test Data

```kotlin
@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()
    GlobalFactoryRegistry.clear()

    // Create reference data
    val admin = dsl.factory<UserRecord>().create(mapOf(
        "role" to "ADMIN"
    ))
}
```

### Verify Relationships

```kotlin
@Test
fun `user has posts`() {
    val user = dsl.factory<UserRecord>().create()
    val post = dsl.factory<PostRecord>().create(mapOf(
        "userId" to user.id
    ))

    val posts = postRepository.findByUserId(user.id)
    assertThat(posts).hasSize(1)
}
```

## Next Steps

- Learn about [Sequences](03-sequences.md) for unique values
- See [Traits](04-traits.md) for attribute variations
- Check [Callbacks](05-callbacks.md) for lifecycle hooks
- Review [examples/BasicExample.kt](../../examples/src/test/kotlin/io/github/krhrtky/faktory/examples/BasicExampleTest.kt)
