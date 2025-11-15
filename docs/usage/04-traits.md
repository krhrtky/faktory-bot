# Traits

Traits define attribute variations without creating separate factories. They allow you to modify factory attributes for specific test scenarios.

## Basic Usage

### Defining a Trait

```kotlin
factory<UserRecord> {
    name = "Regular User"
    email = "user@example.com"
    age = 25

    trait("admin") {
        attribute("name", "Admin User")
        attribute("age", 35)
    }
}
```

### Using a Trait

```kotlin
val regularUser = dsl.factory<UserRecord>().build()
val adminUser = dsl.factory<UserRecord>().build("admin")

assertThat(regularUser.name).isEqualTo("Regular User")
assertThat(regularUser.age).isEqualTo(25)

assertThat(adminUser.name).isEqualTo("Admin User")
assertThat(adminUser.age).isEqualTo(35)
assertThat(adminUser.email).isEqualTo("user@example.com")  // Inherited
```

## Trait Composition

### Multiple Traits

Apply multiple traits to combine their effects:

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"
    age = 25

    trait("verified") {
        attribute("emailVerified", true)
        attribute("email", "verified@example.com")
    }

    trait("senior") {
        attribute("age", 60)
    }
}

val user = dsl.factory<UserRecord>().build("verified", "senior")

assertThat(user.emailVerified).isTrue()
assertThat(user.email).isEqualTo("verified@example.com")
assertThat(user.age).isEqualTo(60)
```

### Trait Order Matters

Later traits override earlier ones:

```kotlin
factory<UserRecord> {
    age = 25

    trait("young") {
        attribute("age", 18)
    }

    trait("old") {
        attribute("age", 70)
    }
}

val user1 = dsl.factory<UserRecord>().build("young", "old")
assertThat(user1.age).isEqualTo(70)  // "old" wins

val user2 = dsl.factory<UserRecord>().build("old", "young")
assertThat(user2.age).isEqualTo(18)  // "young" wins
```

## Traits with Callbacks

Traits can define their own callbacks:

```kotlin
var callbackExecuted = false

factory<UserRecord> {
    name = "User"
    email = "user@example.com"
    age = 25

    trait("withCallback") {
        afterCreate { user, _ ->
            callbackExecuted = true
            println("User created: ${user.name}")
        }
    }
}

dsl.factory<UserRecord>().create("withCallback")

assertThat(callbackExecuted).isTrue()
```

### Callback Execution Order

Base factory callbacks execute first, then trait callbacks:

```kotlin
val executionOrder = mutableListOf<String>()

factory<UserRecord> {
    name = "User"

    afterCreate { _, _ ->
        executionOrder.add("base")
    }

    trait("t1") {
        afterCreate { _, _ ->
            executionOrder.add("t1")
        }
    }

    trait("t2") {
        afterCreate { _, _ ->
            executionOrder.add("t2")
        }
    }
}

dsl.factory<UserRecord>().create("t1", "t2")

assertThat(executionOrder).containsExactly("base", "t1", "t2")
```

## Traits with Transients

Traits can define transient attributes:

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    transient {
        set("postsCount", 0)
    }

    trait("withPosts") {
        transient {
            set("postsCount", 10)
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
}

val user = dsl.factory<UserRecord>().create("withPosts")

val posts = dsl.selectFrom(POSTS)
    .where(POSTS.USER_ID.eq(user.id))
    .fetch()

assertThat(posts).hasSize(10)
```

## Common Trait Patterns

### Role-Based Traits

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    role = "USER"

    trait("admin") {
        attribute("role", "ADMIN")
    }

    trait("moderator") {
        attribute("role", "MODERATOR")
    }

    trait("guest") {
        attribute("role", "GUEST")
    }
}

val admin = dsl.factory<UserRecord>().create("admin")
val mod = dsl.factory<UserRecord>().create("moderator")
```

### Status-Based Traits

```kotlin
factory<OrderRecord> {
    status = "PENDING"
    total = 100

    trait("completed") {
        attribute("status", "COMPLETED")
        attribute("completedAt", LocalDateTime.now())
    }

    trait("cancelled") {
        attribute("status", "CANCELLED")
        attribute("cancelledAt", LocalDateTime.now())
    }
}

val completed = dsl.factory<OrderRecord>().create("completed")
val cancelled = dsl.factory<OrderRecord>().create("cancelled")
```

### Flag-Based Traits

```kotlin
factory<UserRecord> {
    isActive = true
    isVerified = false
    isPremium = false

    trait("verified") {
        attribute("isVerified", true)
    }

    trait("premium") {
        attribute("isPremium", true)
    }

    trait("inactive") {
        attribute("isActive", false)
    }
}

val premiumUser = dsl.factory<UserRecord>().create("verified", "premium")
assertThat(premiumUser.isVerified).isTrue()
assertThat(premiumUser.isPremium).isTrue()
```

### With Associations

```kotlin
factory<PostRecord> {
    title = "Post Title"
    content = "Post Content"

    trait("published") {
        attribute("publishedAt", LocalDateTime.now())
        attribute("status", "PUBLISHED")
    }

    trait("withComments") {
        afterCreate { post, _ ->
            dsl.factory<CommentRecord>().createList(5, mapOf(
                "postId" to post.id
            ))
        }
    }
}

val post = dsl.factory<PostRecord>().create("published", "withComments")
```

## Traits with Overrides

Overrides take precedence over traits:

```kotlin
factory<UserRecord> {
    age = 25

    trait("senior") {
        attribute("age", 60)
    }
}

val user = dsl.factory<UserRecord>().build("senior", overrides = mapOf(
    "age" to 45
))

assertThat(user.age).isEqualTo(45)  // Override wins
```

## Traits with Sequences

Traits can use sequences:

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    trait("sequenced") {
        sequenceAttr("email") { n -> "sequenced${n}@example.com" }
    }
}

val user1 = dsl.factory<UserRecord>().build("sequenced")
val user2 = dsl.factory<UserRecord>().build("sequenced")

assertThat(user1.email).matches("sequenced\\d+@example.com")
assertThat(user2.email).matches("sequenced\\d+@example.com")
assertThat(user1.email).isNotEqualTo(user2.email)
```

## Nested Traits Not Supported

Traits cannot define other traits:

```kotlin
// ❌ NOT SUPPORTED
factory<UserRecord> {
    trait("admin") {
        trait("superAdmin") {  // ERROR
            attribute("role", "SUPER_ADMIN")
        }
    }
}

// ✅ Define at top level instead
factory<UserRecord> {
    trait("admin") {
        attribute("role", "ADMIN")
    }

    trait("superAdmin") {
        attribute("role", "SUPER_ADMIN")
    }
}
```

## Best Practices

### 1. Use Traits for Variations

```kotlin
// ✅ Single factory with traits
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    trait("admin") { ... }
    trait("verified") { ... }
    trait("premium") { ... }
}

// ❌ Multiple factories for variations
factory<UserRecord>("admin") { ... }
factory<UserRecord>("verified") { ... }
factory<UserRecord>("premium") { ... }
```

### 2. Keep Traits Focused

```kotlin
// ✅ Single-purpose traits
trait("verified") {
    attribute("emailVerified", true)
    attribute("verifiedAt", LocalDateTime.now())
}

trait("premium") {
    attribute("isPremium", true)
    attribute("premiumSince", LocalDateTime.now())
}

// ❌ Multi-purpose trait
trait("verifiedPremium") {
    attribute("emailVerified", true)
    attribute("verifiedAt", LocalDateTime.now())
    attribute("isPremium", true)
    attribute("premiumSince", LocalDateTime.now())
}
```

### 3. Combine Traits for Complex Scenarios

```kotlin
@Test
fun `premium verified user can access premium content`() {
    val user = dsl.factory<UserRecord>().create("verified", "premium")

    val canAccess = premiumContentService.canAccess(user)
    assertThat(canAccess).isTrue()
}
```

### 4. Use Descriptive Names

```kotlin
// ✅ Clear intent
trait("emailVerified") { ... }
trait("accountSuspended") { ... }
trait("premiumSubscription") { ... }

// ❌ Unclear
trait("v") { ... }
trait("s") { ... }
trait("p") { ... }
```

## Testing Traits

```kotlin
@Test
fun `admin trait overrides role`() {
    factory<UserRecord> {
        role = "USER"

        trait("admin") {
            attribute("role", "ADMIN")
        }
    }

    val regularUser = dsl.factory<UserRecord>().build()
    val adminUser = dsl.factory<UserRecord>().build("admin")

    assertThat(regularUser.role).isEqualTo("USER")
    assertThat(adminUser.role).isEqualTo("ADMIN")
}

@Test
fun `multiple traits combine correctly`() {
    factory<UserRecord> {
        isVerified = false
        isPremium = false

        trait("verified") {
            attribute("isVerified", true)
        }

        trait("premium") {
            attribute("isPremium", true)
        }
    }

    val user = dsl.factory<UserRecord>().build("verified", "premium")

    assertThat(user.isVerified).isTrue()
    assertThat(user.isPremium).isTrue()
}
```

## Next Steps

- Learn about [Callbacks](05-callbacks.md) for lifecycle hooks
- See [Transients](06-transients.md) for temporary attributes
- Check [examples/TraitExample.kt](../../examples/src/main/kotlin/com/example/faktory/examples/TraitExample.kt)
