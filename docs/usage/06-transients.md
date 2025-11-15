# Transients

Transient attributes are temporary values that control callback behavior without being persisted to the database.

## Basic Usage

### Defining Transients

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    transient {
        set("postsCount", 5)
        set("verified", true)
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("postsCount") as? Int ?: 0
        repeat(count) {
            dsl.factory<PostRecord>().create(mapOf(
                "userId" to user.id
            ))
        }
    }
}

val user = dsl.factory<UserRecord>().create()
// User created with 5 posts
```

### Accessing Transients in Callbacks

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("metadata", "test-data")
    }

    afterCreate { user, transients ->
        val metadata = transients.getOrNull("metadata") as? String
        println("Metadata: $metadata")
    }
}
```

## TransientContext API

### getOrNull(key: String)

Returns the value or null if not present:

```kotlin
afterCreate { user, transients ->
    val count = transients.getOrNull("postsCount") as? Int
    if (count != null) {
        repeat(count) { ... }
    }
}
```

### get(key: String)

Returns the value or throws if not present:

```kotlin
afterCreate { user, transients ->
    val count = transients.get("postsCount") as Int  // Throws if missing
    repeat(count) { ... }
}
```

### contains(key: String)

Checks if a key exists:

```kotlin
afterCreate { user, transients ->
    if (transients.contains("sendEmail")) {
        emailService.send(user.email)
    }
}
```

## Overriding Transients

Transients can be overridden when building/creating records:

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("postsCount", 5)
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("postsCount") as Int
        repeat(count) {
            dsl.factory<PostRecord>().create(mapOf("userId" to user.id))
        }
    }
}

// Default: 5 posts
val user1 = dsl.factory<UserRecord>().create()

// Override: 10 posts
val user2 = dsl.factory<UserRecord>().create(mapOf(
    "postsCount" to 10
))

// Override: 0 posts
val user3 = dsl.factory<UserRecord>().create(mapOf(
    "postsCount" to 0
))
```

## Common Patterns

### Controlling Associations

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("postsCount", 0)
        set("commentsCount", 0)
    }

    afterCreate { user, transients ->
        val posts = transients.getOrNull("postsCount") as? Int ?: 0
        val comments = transients.getOrNull("commentsCount") as? Int ?: 0

        repeat(posts) {
            dsl.factory<PostRecord>().create(mapOf("userId" to user.id))
        }

        repeat(comments) {
            dsl.factory<CommentRecord>().create(mapOf("userId" to user.id))
        }
    }
}

// User with 5 posts and 10 comments
val user = dsl.factory<UserRecord>().create(mapOf(
    "postsCount" to 5,
    "commentsCount" to 10
))
```

### Conditional Behavior

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    transient {
        set("sendWelcomeEmail", false)
        set("createProfile", true)
    }

    afterCreate { user, transients ->
        val sendEmail = transients.getOrNull("sendWelcomeEmail") as? Boolean ?: false
        if (sendEmail) {
            emailService.sendWelcome(user.email)
        }

        val createProfile = transients.getOrNull("createProfile") as? Boolean ?: false
        if (createProfile) {
            dsl.factory<ProfileRecord>().create(mapOf("userId" to user.id))
        }
    }
}

// User with welcome email
val user = dsl.factory<UserRecord>().create(mapOf(
    "sendWelcomeEmail" to true
))
```

### Test Metadata

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("testScenario", "default")
    }

    afterCreate { user, transients ->
        val scenario = transients.getOrNull("testScenario") as? String
        println("Test scenario: $scenario")
    }
}

@Test
fun `admin scenario`() {
    val admin = dsl.factory<UserRecord>().create(mapOf(
        "testScenario" to "admin_permissions"
    ))
}
```

### Dynamic Data Generation

```kotlin
factory<PostRecord> {
    title = "Post Title"
    content = "Post Content"

    transient {
        set("wordCount", 100)
    }

    afterBuild { post, transients ->
        val wordCount = transients.getOrNull("wordCount") as? Int ?: 100
        post.content = generateLoremIpsum(wordCount)
    }
}

val shortPost = dsl.factory<PostRecord>().build(mapOf(
    "wordCount" to 50
))

val longPost = dsl.factory<PostRecord>().build(mapOf(
    "wordCount" to 500
))
```

## Transients in Traits

Traits can define their own transients:

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("postsCount", 0)
    }

    trait("withPosts") {
        transient {
            set("postsCount", 5)
        }
    }

    trait("withManyPosts") {
        transient {
            set("postsCount", 50)
        }
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("postsCount") as Int
        repeat(count) {
            dsl.factory<PostRecord>().create(mapOf("userId" to user.id))
        }
    }
}

val user1 = dsl.factory<UserRecord>().create()                // 0 posts
val user2 = dsl.factory<UserRecord>().create("withPosts")     // 5 posts
val user3 = dsl.factory<UserRecord>().create("withManyPosts") // 50 posts
```

## Type Safety

Transients are stored as `Any?`, so type casting is required:

```kotlin
factory<UserRecord> {
    transient {
        set("count", 5)
        set("enabled", true)
        set("tags", listOf("a", "b", "c"))
    }

    afterCreate { user, transients ->
        val count = transients.getOrNull("count") as? Int ?: 0
        val enabled = transients.getOrNull("enabled") as? Boolean ?: false
        val tags = transients.getOrNull("tags") as? List<*> ?: emptyList<String>()

        println("Count: $count, Enabled: $enabled, Tags: $tags")
    }
}
```

### Helper Functions

```kotlin
fun TransientContext.getInt(key: String, default: Int = 0): Int =
    getOrNull(key) as? Int ?: default

fun TransientContext.getBool(key: String, default: Boolean = false): Boolean =
    getOrNull(key) as? Boolean ?: default

fun TransientContext.getString(key: String, default: String = ""): String =
    getOrNull(key) as? String ?: default

// Usage
afterCreate { user, transients ->
    val count = transients.getInt("postsCount", 5)
    val enabled = transients.getBool("sendEmail", false)
}
```

## Transients vs Attributes

### Transients

- Not persisted to database
- Used only in callbacks
- Can be any type
- No schema validation

```kotlin
factory<UserRecord> {
    transient {
        set("postsCount", 5)  // Not a DB field
    }
}
```

### Attributes

- Persisted to database
- Must match jOOQ Record fields
- Type-checked by jOOQ
- Schema validation

```kotlin
factory<UserRecord> {
    name = "User"        // DB field
    email = "user@..."   // DB field
}
```

## Best Practices

### 1. Use Descriptive Names

```kotlin
// ✅ Clear intent
transient {
    set("associatedPostsCount", 5)
    set("sendWelcomeEmail", true)
    set("generateTestData", false)
}

// ❌ Unclear
transient {
    set("c", 5)
    set("e", true)
    set("d", false)
}
```

### 2. Provide Defaults

```kotlin
// ✅ Handles missing values
afterCreate { user, transients ->
    val count = transients.getOrNull("postsCount") as? Int ?: 0
}

// ❌ Crashes if missing
afterCreate { user, transients ->
    val count = transients.get("postsCount") as Int  // Throws
}
```

### 3. Document Transient Behavior

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        // Controls number of posts created in afterCreate callback
        set("postsCount", 0)

        // If true, sends welcome email after user creation
        set("sendWelcomeEmail", false)
    }

    afterCreate { user, transients ->
        // ...
    }
}
```

### 4. Use Traits for Common Configurations

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("postsCount", 0)
    }

    trait("withPosts") {
        transient {
            set("postsCount", 5)
        }
    }

    trait("powerUser") {
        transient {
            set("postsCount", 50)
            set("commentsCount", 100)
        }
    }
}

// Usage
val regularUser = dsl.factory<UserRecord>().create()
val userWithPosts = dsl.factory<UserRecord>().create("withPosts")
val powerUser = dsl.factory<UserRecord>().create("powerUser")
```

## Testing with Transients

```kotlin
@Test
fun `user creation with posts`() {
    factory<UserRecord> {
        name = "User"

        transient {
            set("postsCount", 0)
        }

        afterCreate { user, transients ->
            val count = transients.getOrNull("postsCount") as? Int ?: 0
            repeat(count) {
                dsl.factory<PostRecord>().create(mapOf("userId" to user.id))
            }
        }
    }

    val user = dsl.factory<UserRecord>().create(mapOf(
        "postsCount" to 10
    ))

    val posts = dsl.selectFrom(POSTS)
        .where(POSTS.USER_ID.eq(user.id))
        .fetch()

    assertThat(posts).hasSize(10)
}

@Test
fun `conditional email sending`() {
    var emailSent = false

    factory<UserRecord> {
        name = "User"

        transient {
            set("sendEmail", false)
        }

        afterCreate { user, transients ->
            val send = transients.getOrNull("sendEmail") as? Boolean ?: false
            if (send) {
                emailSent = true
            }
        }
    }

    dsl.factory<UserRecord>().create(mapOf("sendEmail" to true))

    assertThat(emailSent).isTrue()
}
```

## Common Pitfalls

### Confusing Transients with Attributes

```kotlin
// ❌ Transient used as attribute
factory<UserRecord> {
    transient {
        set("name", "User")  // Won't be persisted!
    }
}

// ✅ Use attribute instead
factory<UserRecord> {
    name = "User"
}
```

### Not Handling Missing Transients

```kotlin
// ❌ Crashes if not provided
afterCreate { user, transients ->
    val count = transients.get("postsCount") as Int
}

// ✅ Provides default
afterCreate { user, transients ->
    val count = transients.getOrNull("postsCount") as? Int ?: 0
}
```

### Type Mismatch

```kotlin
// ❌ Wrong type
transient {
    set("count", "5")  // String
}

afterCreate { user, transients ->
    val count = transients.getOrNull("count") as? Int  // null!
}

// ✅ Correct type
transient {
    set("count", 5)  // Int
}
```

## Next Steps

- Learn about [Transactions](07-transactions.md) for managing database state
- See [Advanced Topics](08-advanced.md) for complex scenarios
- Check [examples/TransientExample.kt](../../examples/src/main/kotlin/com/example/faktory/examples/TransientExample.kt)
