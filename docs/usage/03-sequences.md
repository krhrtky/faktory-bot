# Sequences

Sequences generate unique values for factory attributes, ensuring no collisions in tests.

## Basic Usage

### Simple Sequence

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

val user1 = dsl.factory<UserRecord>().build()
val user2 = dsl.factory<UserRecord>().build()
val user3 = dsl.factory<UserRecord>().build()

assertThat(user1.email).isEqualTo("user1@example.com")
assertThat(user2.email).isEqualTo("user2@example.com")
assertThat(user3.email).isEqualTo("user3@example.com")
```

### How It Works

- Each call to `sequence { n -> ... }` increments `n` starting from 1
- Sequences are managed globally by `GlobalSequenceManager`
- Each attribute has an independent sequence counter

## Sequence Patterns

### Email Addresses

```kotlin
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }
}

// user1@example.com, user2@example.com, ...
```

### Usernames

```kotlin
factory<UserRecord> {
    username = sequence { n -> "user_$n" }
}

// user_1, user_2, user_3, ...
```

### Names

```kotlin
factory<UserRecord> {
    name = sequence { n -> "User $n" }
}

// "User 1", "User 2", "User 3", ...
```

### Phone Numbers

```kotlin
factory<UserRecord> {
    phone = sequence { n -> "+1-555-${String.format("%04d", n)}" }
}

// +1-555-0001, +1-555-0002, ...
```

### SKU/Product Codes

```kotlin
factory<ProductRecord> {
    sku = sequence { n -> "PROD-${String.format("%06d", n)}" }
}

// PROD-000001, PROD-000002, ...
```

### Account Numbers

```kotlin
factory<AccountRecord> {
    accountNumber = sequence { n ->
        val padded = String.format("%010d", n)
        "ACCT-$padded"
    }
}

// ACCT-0000000001, ACCT-0000000002, ...
```

## Using sequenceAttr()

For attributes not explicitly defined as properties in the DSL builder:

```kotlin
factory<UserRecord> {
    sequenceAttr("customField") { n -> "value_$n" }
}

val user = dsl.factory<UserRecord>().build()
// user.customField => "value_1"
```

## Multiple Sequences in One Factory

Each sequence maintains its own counter:

```kotlin
factory<UserRecord> {
    username = sequence { n -> "user_$n" }
    email = sequence { n -> "user${n}@example.com" }
    referralCode = sequence { n -> "REF${String.format("%06d", n)}" }
}

val user1 = dsl.factory<UserRecord>().build()
val user2 = dsl.factory<UserRecord>().build()

// user1: username=user_1, email=user1@example.com, referralCode=REF000001
// user2: username=user_2, email=user2@example.com, referralCode=REF000002
```

## Sequences with buildList/createList

Sequences work seamlessly with bulk operations:

```kotlin
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

val users = dsl.factory<UserRecord>().buildList(5)

assertThat(users[0].email).isEqualTo("user1@example.com")
assertThat(users[1].email).isEqualTo("user2@example.com")
assertThat(users[4].email).isEqualTo("user5@example.com")
```

## Resetting Sequences

Sequences must be reset between tests to ensure deterministic values.

### In @BeforeEach

```kotlin
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager

@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()
}

@Test
fun test1() {
    val user = dsl.factory<UserRecord>().build()
    assertThat(user.email).isEqualTo("user1@example.com")  // ✅
}

@Test
fun test2() {
    val user = dsl.factory<UserRecord>().build()
    assertThat(user.email).isEqualTo("user1@example.com")  // ✅
}
```

### Without Reset (Anti-Pattern)

```kotlin
// ❌ NO RESET
@Test
fun test1() {
    val user = dsl.factory<UserRecord>().build()
    assertThat(user.email).isEqualTo("user1@example.com")  // ✅
}

@Test
fun test2() {
    val user = dsl.factory<UserRecord>().build()
    assertThat(user.email).isEqualTo("user1@example.com")  // ❌ Fails: user2@example.com
}
```

## Sequence Scope

Sequences are scoped per attribute name:

```kotlin
factory<UserRecord> {
    sequenceAttr("email") { n -> "user${n}@example.com" }
}

factory<AdminRecord> {
    sequenceAttr("email") { n -> "admin${n}@example.com" }
}

val user = dsl.factory<UserRecord>().build()   // email: user1@example.com
val admin = dsl.factory<AdminRecord>().build() // email: admin2@example.com (shares counter!)
```

To avoid sharing, use different attribute names or reset between builds.

## Advanced Patterns

### Random with Seed

For reproducible randomness:

```kotlin
factory<UserRecord> {
    email = sequence { n ->
        val random = Random(n.toLong())
        "user_${random.nextInt(1000)}@example.com"
    }
}
```

### Composite Keys

```kotlin
factory<OrderRecord> {
    orderNumber = sequence { n ->
        val timestamp = System.currentTimeMillis() / 1000
        "${timestamp}-${String.format("%04d", n)}"
    }
}

// 1634567890-0001, 1634567890-0002, ...
```

### Date Sequences

```kotlin
factory<EventRecord> {
    scheduledAt = sequence { n ->
        LocalDateTime.now().plusDays(n.toLong())
    }
}

// Today, Tomorrow, Day After Tomorrow, ...
```

### Incrementing IDs (Non-DB)

```kotlin
factory<ApiRequestRecord> {
    requestId = sequence { n -> n }
}

// 1, 2, 3, 4, ...
```

## Sequences with Overrides

Overrides take precedence over sequences:

```kotlin
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }
}

val user = dsl.factory<UserRecord>().build(mapOf(
    "email" to "custom@example.com"
))

assertThat(user.email).isEqualTo("custom@example.com")
```

## Thread Safety

`GlobalSequenceManager` uses `AtomicInteger` for thread-safe sequence generation:

```kotlin
runBlocking {
    val jobs = (1..100).map {
        launch(Dispatchers.IO) {
            dsl.factory<UserRecord>().build()
        }
    }
    jobs.joinAll()
}

// All 100 users have unique email addresses
```

## Best Practices

### 1. Always Reset in @BeforeEach

```kotlin
@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()
    GlobalFactoryRegistry.clear()
}
```

### 2. Use Descriptive Patterns

```kotlin
// ✅ Clear intent
email = sequence { n -> "test_user_${n}@example.com" }

// ❌ Unclear
email = sequence { n -> "x${n}@y.z" }
```

### 3. Format Numbers with Padding

```kotlin
// ✅ Sortable
sku = sequence { n -> "PROD-${String.format("%06d", n)}" }
// PROD-000001, PROD-000002

// ❌ Not sortable
sku = sequence { n -> "PROD-$n" }
// PROD-1, PROD-2, PROD-10 (sorts incorrectly)
```

### 4. Avoid Complex Logic in Sequences

```kotlin
// ❌ Too complex
email = sequence { n ->
    val domain = if (n % 2 == 0) "even.com" else "odd.com"
    "user${n}@${domain}"
}

// ✅ Use traits instead
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }

    trait("evenDomain") {
        attribute("email") { context ->
            val n = context.sequenceManager.next("email")
            "user${n}@even.com"
        }
    }
}
```

### 5. Test Sequence Uniqueness

```kotlin
@Test
fun `sequences generate unique values`() {
    val users = dsl.factory<UserRecord>().buildList(100)
    val emails = users.map { it.email }.toSet()

    assertThat(emails).hasSize(100)
}
```

## Common Pitfalls

### Forgetting to Reset

```kotlin
// ❌ Sequences carry over between tests
@Test
fun test1() {
    val user = dsl.factory<UserRecord>().build()
    // email: user1@example.com
}

@Test
fun test2() {
    val user = dsl.factory<UserRecord>().build()
    // email: user2@example.com (unexpected!)
}
```

### Using Same Sequence Name

```kotlin
factory<UserRecord> {
    sequenceAttr("id") { n -> n }
}

factory<PostRecord> {
    sequenceAttr("id") { n -> n }  // Shares counter with UserRecord!
}

val user = dsl.factory<UserRecord>().build()  // id: 1
val post = dsl.factory<PostRecord>().build()  // id: 2 (not 1!)
```

**Solution**: Use unique names or scoped sequences.

## Next Steps

- Learn about [Traits](04-traits.md) for attribute variations
- See [Callbacks](05-callbacks.md) for sequence-dependent logic
- Check [examples/SequenceExample.kt](../../examples/src/main/kotlin/com/example/faktory/examples/SequenceExample.kt)
