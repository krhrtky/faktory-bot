# Callbacks

Callbacks are lifecycle hooks that execute at specific points during record creation. They enable setup of related records, logging, and custom validation.

## Callback Types

| Callback | Trigger | Use Case |
|----------|---------|----------|
| `afterBuild` | After record built (build/create) | Set computed fields, logging |
| `beforeCreate` | Before DB insert (create only) | Validation, pre-processing |
| `afterCreate` | After DB insert (create only) | Create associations, audit logs |

## afterBuild

Executes after the record is built, before database persistence.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "John Doe"
    email = "john@example.com"

    afterBuild { user, _ ->
        println("Built user: ${user.name}")
    }
}

val user = dsl.factory<UserRecord>().build()
// Output: Built user: John Doe
```

### Setting Computed Fields

```kotlin
factory<UserRecord> {
    firstName = "John"
    lastName = "Doe"

    afterBuild { user, _ ->
        user.fullName = "${user.firstName} ${user.lastName}"
    }
}

val user = dsl.factory<UserRecord>().build()
assertThat(user.fullName).isEqualTo("John Doe")
```

### Executes for Both build() and create()

```kotlin
var buildCount = 0

factory<UserRecord> {
    name = "User"

    afterBuild { _, _ ->
        buildCount++
    }
}

dsl.factory<UserRecord>().build()    // buildCount = 1
dsl.factory<UserRecord>().create()   // buildCount = 2
```

## beforeCreate

Executes before database insert, only for `create()`.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    beforeCreate { user, _ ->
        user.createdAt = LocalDateTime.now()
        println("Creating user: ${user.name}")
    }
}

val user = dsl.factory<UserRecord>().create()
// Output: Creating user: User
assertThat(user.createdAt).isNotNull()
```

### Validation

```kotlin
factory<UserRecord> {
    email = "user@example.com"

    beforeCreate { user, _ ->
        require(user.email.contains("@")) {
            "Invalid email: ${user.email}"
        }
    }
}

// ✅ Valid
val user1 = dsl.factory<UserRecord>().create()

// ❌ Throws exception
assertThrows<IllegalArgumentException> {
    dsl.factory<UserRecord>().create(mapOf(
        "email" to "invalid-email"
    ))
}
```

### Only for create(), Not build()

```kotlin
var beforeCreateCalled = false

factory<UserRecord> {
    name = "User"

    beforeCreate { _, _ ->
        beforeCreateCalled = true
    }
}

dsl.factory<UserRecord>().build()
assertThat(beforeCreateCalled).isFalse()  // Not called

dsl.factory<UserRecord>().create()
assertThat(beforeCreateCalled).isTrue()   // Called
```

## afterCreate

Executes after database insert, only for `create()`.

### Basic Example

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    afterCreate { user, _ ->
        println("Created user with ID: ${user.id}")
    }
}

val user = dsl.factory<UserRecord>().create()
// Output: Created user with ID: 123
```

### Creating Associations

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    afterCreate { user, _ ->
        dsl.factory<PostRecord>().createList(5, mapOf(
            "userId" to user.id
        ))
    }
}

val user = dsl.factory<UserRecord>().create()

val posts = dsl.selectFrom(POSTS)
    .where(POSTS.USER_ID.eq(user.id))
    .fetch()

assertThat(posts).hasSize(5)
```

### Audit Logging

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user, _ ->
        dsl.insertInto(AUDIT_LOG)
            .set(AUDIT_LOG.ACTION, "CREATE_USER")
            .set(AUDIT_LOG.ENTITY_ID, user.id)
            .set(AUDIT_LOG.TIMESTAMP, LocalDateTime.now())
            .execute()
    }
}

val user = dsl.factory<UserRecord>().create()

val log = dsl.selectFrom(AUDIT_LOG)
    .where(AUDIT_LOG.ENTITY_ID.eq(user.id))
    .fetchOne()

assertThat(log).isNotNull()
assertThat(log.action).isEqualTo("CREATE_USER")
```

## Callback Execution Order

### Single Factory

```kotlin
val executionOrder = mutableListOf<String>()

factory<UserRecord> {
    name = "User"

    afterBuild { _, _ ->
        executionOrder.add("afterBuild")
    }

    beforeCreate { _, _ ->
        executionOrder.add("beforeCreate")
    }

    afterCreate { _, _ ->
        executionOrder.add("afterCreate")
    }
}

dsl.factory<UserRecord>().create()

assertThat(executionOrder).containsExactly(
    "afterBuild",
    "beforeCreate",
    "afterCreate"
)
```

### With Traits

Base factory callbacks execute first, then trait callbacks:

```kotlin
val executionOrder = mutableListOf<String>()

factory<UserRecord> {
    name = "User"

    afterCreate { _, _ ->
        executionOrder.add("base")
    }

    trait("withCallback") {
        afterCreate { _, _ ->
            executionOrder.add("trait")
        }
    }
}

dsl.factory<UserRecord>().create("withCallback")

assertThat(executionOrder).containsExactly("base", "trait")
```

## Callbacks with Transients

Callbacks receive a `TransientContext` with transient attributes:

### Basic Example

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("postsCount", 5)
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("postsCount") as Int
        repeat(count) {
            dsl.factory<PostRecord>().create(mapOf(
                "userId" to user.id
            ))
        }
    }
}

val user = dsl.factory<UserRecord>().create()

val posts = dsl.selectFrom(POSTS)
    .where(POSTS.USER_ID.eq(user.id))
    .fetch()

assertThat(posts).hasSize(5)
```

### Conditional Logic

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("sendWelcomeEmail", true)
    }

    afterCreate { user, transients ->
        val sendEmail = transients.getOrNull("sendWelcomeEmail") as? Boolean ?: false
        if (sendEmail) {
            emailService.sendWelcome(user.email)
        }
    }
}

// With email
dsl.factory<UserRecord>().create()

// Without email
dsl.factory<UserRecord>().create(mapOf(
    "sendWelcomeEmail" to false
))
```

## Common Patterns

### Setup Related Records

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user, _ ->
        // Create profile
        dsl.factory<ProfileRecord>().create(mapOf(
            "userId" to user.id
        ))

        // Create settings
        dsl.factory<SettingsRecord>().create(mapOf(
            "userId" to user.id
        ))
    }
}

val user = dsl.factory<UserRecord>().create()

assertThat(user.profile).isNotNull()
assertThat(user.settings).isNotNull()
```

### Generate Test Data

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("withOrders", false)
    }

    afterCreate { user, transients ->
        val withOrders = transients.getOrNull("withOrders") as? Boolean ?: false
        if (withOrders) {
            dsl.factory<OrderRecord>().createList(10, mapOf(
                "userId" to user.id
            ))
        }
    }
}

val userWithOrders = dsl.factory<UserRecord>().create(mapOf(
    "withOrders" to true
))
```

### Custom Validation

```kotlin
factory<ProductRecord> {
    name = "Product"
    price = 100

    beforeCreate { product, _ ->
        require(product.price > 0) {
            "Price must be positive"
        }
        require(product.name.isNotBlank()) {
            "Name cannot be blank"
        }
    }
}

assertThrows<IllegalArgumentException> {
    dsl.factory<ProductRecord>().create(mapOf(
        "price" to -10
    ))
}
```

### Timestamp Management

```kotlin
factory<UserRecord> {
    name = "User"

    beforeCreate { user, _ ->
        user.createdAt = LocalDateTime.now()
        user.updatedAt = LocalDateTime.now()
    }

    afterCreate { user, _ ->
        // createdAt and updatedAt are set
        println("User created at: ${user.createdAt}")
    }
}
```

## Multiple Callbacks of Same Type

Multiple callbacks of the same type execute in order:

```kotlin
val executionOrder = mutableListOf<String>()

factory<UserRecord> {
    name = "User"

    afterCreate { _, _ ->
        executionOrder.add("callback1")
    }

    afterCreate { _, _ ->
        executionOrder.add("callback2")
    }

    afterCreate { _, _ ->
        executionOrder.add("callback3")
    }
}

dsl.factory<UserRecord>().create()

assertThat(executionOrder).containsExactly(
    "callback1",
    "callback2",
    "callback3"
)
```

## Callbacks with buildList/createList

Callbacks execute for each record:

```kotlin
var callbackCount = 0

factory<UserRecord> {
    name = "User"

    afterCreate { _, _ ->
        callbackCount++
    }
}

dsl.factory<UserRecord>().createList(5)

assertThat(callbackCount).isEqualTo(5)
```

## Best Practices

### 1. Keep Callbacks Simple

```kotlin
// ✅ Simple, focused
afterCreate { user, _ ->
    auditLog.log("CREATE_USER", user.id)
}

// ❌ Too complex
afterCreate { user, _ ->
    auditLog.log("CREATE_USER", user.id)
    emailService.sendWelcome(user.email)
    notificationService.notify(user.id)
    analyticsService.track(user.id)
    // ... 50 more lines
}
```

### 2. Use Transients for Configuration

```kotlin
// ✅ Configurable via transients
factory<UserRecord> {
    transient {
        set("postsCount", 5)
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("postsCount") as Int
        repeat(count) { ... }
    }
}

// ❌ Hardcoded
afterCreate { user, _ ->
    repeat(5) { ... }
}
```

### 3. Validate in beforeCreate

```kotlin
// ✅ Fail fast
beforeCreate { user, _ ->
    require(user.email.contains("@")) {
        "Invalid email"
    }
}

// ❌ Validate in afterCreate (too late)
afterCreate { user, _ ->
    require(user.email.contains("@"))
}
```

### 4. Avoid Side Effects in afterBuild

```kotlin
// ✅ Safe
afterBuild { user, _ ->
    user.fullName = "${user.firstName} ${user.lastName}"
}

// ❌ Side effects (DB writes)
afterBuild { user, _ ->
    dsl.insertInto(AUDIT_LOG).execute()  // Don't do this
}
```

## Common Pitfalls

### Modifying Record in afterCreate

```kotlin
// ❌ Changes won't persist
afterCreate { user, _ ->
    user.name = "Modified"  // Lost after function returns
}

// ✅ Use beforeCreate instead
beforeCreate { user, _ ->
    user.name = "Modified"  // Persisted to DB
}
```

### Circular Dependencies

```kotlin
// ❌ Infinite recursion
factory<UserRecord> {
    afterCreate { user, _ ->
        dsl.factory<PostRecord>().create(mapOf(
            "userId" to user.id
        ))
    }
}

factory<PostRecord> {
    afterCreate { post, _ ->
        dsl.factory<UserRecord>().create(mapOf(
            "id" to post.userId
        ))
    }
}

// Stack overflow!
dsl.factory<UserRecord>().create()
```

## Next Steps

- Learn about [Transients](06-transients.md) for callback configuration
- See [Transactions](07-transactions.md) for managing database state
- Check [examples/CallbackExample.kt](../../examples/src/main/kotlin/com/example/faktory/examples/CallbackExample.kt)
