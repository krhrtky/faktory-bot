# Advanced Topics

## Complex Factory Patterns

### Factory Inheritance

While Faktory Bot doesn't support explicit factory inheritance, you can achieve similar results:

```kotlin
// Base factory configuration
fun baseUserFactory(): Map<String, Any?> = mapOf(
    "isActive" to true,
    "createdAt" to LocalDateTime.now()
)

factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    baseUserFactory().forEach { (key, value) ->
        attribute(key, value)
    }
}

factory<AdminRecord> {
    name = "Admin"
    email = sequence { n -> "admin${n}@example.com" }

    baseUserFactory().forEach { (key, value) ->
        attribute(key, value)
    }
}
```

### Dynamic Attribute Generation

```kotlin
factory<UserRecord> {
    name = "User"

    attribute("metadata") { context ->
        val n = context.sequenceManager.next("user")
        mapOf(
            "userId" to "user_$n",
            "timestamp" to System.currentTimeMillis(),
            "random" to UUID.randomUUID().toString()
        )
    }
}
```

### Conditional Attributes

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"

    attribute("status") { context ->
        val n = context.sequenceManager.next("user")
        if (n % 2 == 0) "ACTIVE" else "INACTIVE"
    }
}
```

## Working with Associations

### One-to-Many

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user, _ ->
        dsl.factory<PostRecord>().createList(5, mapOf(
            "userId" to user.id
        ))
    }
}

@Test
fun `user has posts`() {
    val user = dsl.factory<UserRecord>().create()

    val posts = dsl.selectFrom(POSTS)
        .where(POSTS.USER_ID.eq(user.id))
        .fetch()

    assertThat(posts).hasSize(5)
}
```

### Many-to-Many

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("groupIds", emptyList<Long>())
    }

    afterCreate { user, transients ->
        val groupIds = transients.getOrNull("groupIds") as? List<*> ?: emptyList<Long>()
        groupIds.forEach { groupId ->
            dsl.insertInto(USER_GROUPS)
                .set(USER_GROUPS.USER_ID, user.id)
                .set(USER_GROUPS.GROUP_ID, groupId as Long)
                .execute()
        }
    }
}

@Test
fun `user belongs to multiple groups`() {
    val group1 = dsl.factory<GroupRecord>().create()
    val group2 = dsl.factory<GroupRecord>().create()

    val user = dsl.factory<UserRecord>().create(mapOf(
        "groupIds" to listOf(group1.id, group2.id)
    ))

    val groups = dsl.select()
        .from(USER_GROUPS)
        .where(USER_GROUPS.USER_ID.eq(user.id))
        .fetch()

    assertThat(groups).hasSize(2)
}
```

### Polymorphic Associations

```kotlin
factory<CommentRecord> {
    content = "Comment"

    transient {
        set("commentableType", "Post")
        set("commentableId", null)
    }

    beforeCreate { comment, transients ->
        val type = transients.getOrNull("commentableType") as? String
        val id = transients.getOrNull("commentableId") as? Long

        comment.commentableType = type
        comment.commentableId = id
    }
}

@Test
fun `comment on post`() {
    val post = dsl.factory<PostRecord>().create()

    val comment = dsl.factory<CommentRecord>().create(mapOf(
        "commentableType" to "Post",
        "commentableId" to post.id
    ))

    assertThat(comment.commentableType).isEqualTo("Post")
    assertThat(comment.commentableId).isEqualTo(post.id)
}
```

## Performance Optimization

### Batch Creation

```kotlin
// ❌ Slow - 100 individual INSERTs
repeat(100) {
    dsl.factory<UserRecord>().create()
}

// ✅ Fast - Single batch INSERT
dsl.factory<UserRecord>().createList(100)
```

### Disable Callbacks for Bulk Operations

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("skipCallbacks", false)
    }

    afterCreate { user, transients ->
        val skip = transients.getOrNull("skipCallbacks") as? Boolean ?: false
        if (!skip) {
            // Expensive operation
            auditLog.log("CREATE_USER", user.id)
        }
    }
}

// Fast bulk creation
val users = dsl.factory<UserRecord>().createList(1000, mapOf(
    "skipCallbacks" to true
))
```

### Lazy Association Loading

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        set("lazyLoadPosts", true)
    }

    afterCreate { user, transients ->
        val lazy = transients.getOrNull("lazyLoadPosts") as? Boolean ?: false
        if (!lazy) {
            dsl.factory<PostRecord>().createList(10, mapOf("userId" to user.id))
        }
    }
}

// Create user without posts (fast)
val user = dsl.factory<UserRecord>().create()

// Create posts later if needed
dsl.factory<PostRecord>().createList(10, mapOf("userId" to user.id))
```

## Testing Strategies

### Arrange-Act-Assert with Factories

```kotlin
@Test
fun `user can delete own post`() {
    // Arrange
    val user = dsl.factory<UserRecord>().create()
    val post = dsl.factory<PostRecord>().create(mapOf(
        "userId" to user.id
    ))

    // Act
    val deleted = postService.delete(post.id, user.id)

    // Assert
    assertThat(deleted).isTrue()
    val found = dsl.selectFrom(POSTS).where(POSTS.ID.eq(post.id)).fetchOne()
    assertThat(found).isNull()
}
```

### Parameterized Tests

```kotlin
@ParameterizedTest
@ValueSource(strings = ["admin", "moderator", "user"])
fun `different roles have different permissions`(role: String) {
    factory<UserRecord> {
        name = "User"

        trait("admin") {
            attribute("role", "ADMIN")
        }

        trait("moderator") {
            attribute("role", "MODERATOR")
        }

        trait("user") {
            attribute("role", "USER")
        }
    }

    val user = dsl.factory<UserRecord>().create(role)

    val permissions = permissionService.getPermissions(user)
    assertThat(permissions).isNotEmpty()
}
```

### Test Data Builders

```kotlin
class UserBuilder(private val dsl: DSLContext) {
    private val overrides = mutableMapOf<String, Any?>()

    fun withName(name: String) = apply {
        overrides["name"] = name
    }

    fun withEmail(email: String) = apply {
        overrides["email"] = email
    }

    fun withRole(role: String) = apply {
        overrides["role"] = role
    }

    fun build(): UserRecord =
        dsl.factory<UserRecord>().build(overrides)

    fun create(): UserRecord =
        dsl.factory<UserRecord>().create(overrides)
}

@Test
fun `user builder pattern`() {
    val user = UserBuilder(dsl)
        .withName("John Doe")
        .withEmail("john@example.com")
        .withRole("ADMIN")
        .create()

    assertThat(user.name).isEqualTo("John Doe")
    assertThat(user.role).isEqualTo("ADMIN")
}
```

## Custom Sequences

### Date Sequences

```kotlin
factory<EventRecord> {
    name = "Event"

    attribute("scheduledAt") { context ->
        val n = context.sequenceManager.next("event_date")
        LocalDateTime.now().plusDays(n.toLong())
    }
}

val events = dsl.factory<EventRecord>().buildList(5)

// Events scheduled on consecutive days
assertThat(events[0].scheduledAt).isAfter(LocalDateTime.now())
assertThat(events[1].scheduledAt).isAfter(events[0].scheduledAt)
```

### Enum Sequences

```kotlin
enum class Status { PENDING, ACTIVE, COMPLETED, CANCELLED }

factory<OrderRecord> {
    total = 100

    attribute("status") { context ->
        val n = context.sequenceManager.next("order_status")
        val statuses = Status.values()
        statuses[n % statuses.size]
    }
}

val orders = dsl.factory<OrderRecord>().buildList(8)

// Cycles through all statuses
assertThat(orders.map { it.status }).containsExactly(
    Status.PENDING, Status.ACTIVE, Status.COMPLETED, Status.CANCELLED,
    Status.PENDING, Status.ACTIVE, Status.COMPLETED, Status.CANCELLED
)
```

### UUID Sequences

```kotlin
factory<ApiKeyRecord> {
    name = "API Key"

    attribute("key") { context ->
        val n = context.sequenceManager.next("api_key")
        UUID.nameUUIDFromBytes("api_key_$n".toByteArray()).toString()
    }
}
```

## Multi-Factory Scenarios

### Complex Test Setup

```kotlin
@Test
fun `blog post with author, comments, and tags`() {
    val author = dsl.factory<UserRecord>().create(mapOf(
        "name" to "Author"
    ))

    val post = dsl.factory<PostRecord>().create(mapOf(
        "userId" to author.id,
        "title" to "My Post"
    ))

    val commenters = dsl.factory<UserRecord>().createList(3)
    val comments = commenters.map { commenter ->
        dsl.factory<CommentRecord>().create(mapOf(
            "postId" to post.id,
            "userId" to commenter.id,
            "content" to "Great post!"
        ))
    }

    val tags = dsl.factory<TagRecord>().createList(5)
    tags.forEach { tag ->
        dsl.insertInto(POST_TAGS)
            .set(POST_TAGS.POST_ID, post.id)
            .set(POST_TAGS.TAG_ID, tag.id)
            .execute()
    }

    val fullPost = postService.getPostWithDetails(post.id)

    assertThat(fullPost.author.name).isEqualTo("Author")
    assertThat(fullPost.comments).hasSize(3)
    assertThat(fullPost.tags).hasSize(5)
}
```

### Factory Composition

```kotlin
class TestDataFactory(private val dsl: DSLContext) {
    fun createBlogPost(
        authorName: String = "Author",
        commentsCount: Int = 0,
        tags: List<String> = emptyList()
    ): PostRecord {
        val author = dsl.factory<UserRecord>().create(mapOf(
            "name" to authorName
        ))

        val post = dsl.factory<PostRecord>().create(mapOf(
            "userId" to author.id
        ))

        repeat(commentsCount) {
            dsl.factory<CommentRecord>().create(mapOf(
                "postId" to post.id,
                "userId" to author.id
            ))
        }

        tags.forEach { tagName ->
            val tag = dsl.factory<TagRecord>().create(mapOf(
                "name" to tagName
            ))

            dsl.insertInto(POST_TAGS)
                .set(POST_TAGS.POST_ID, post.id)
                .set(POST_TAGS.TAG_ID, tag.id)
                .execute()
        }

        return post
    }
}

@Test
fun `using factory composition`() {
    val factory = TestDataFactory(dsl)

    val post = factory.createBlogPost(
        authorName = "John Doe",
        commentsCount = 5,
        tags = listOf("kotlin", "testing", "jooq")
    )

    assertThat(post).isNotNull()
}
```

## Error Handling

### Graceful Degradation

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    afterCreate { user, _ ->
        try {
            emailService.sendWelcome(user.email)
        } catch (e: Exception) {
            println("Failed to send email: ${e.message}")
            // Don't fail factory creation
        }
    }
}
```

### Validation in Callbacks

```kotlin
factory<ProductRecord> {
    name = "Product"
    price = 100

    beforeCreate { product, _ ->
        val errors = mutableListOf<String>()

        if (product.price <= 0) {
            errors.add("Price must be positive")
        }

        if (product.name.isBlank()) {
            errors.add("Name cannot be blank")
        }

        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(
                "Validation failed: ${errors.joinToString(", ")}"
            )
        }
    }
}
```

## Debugging Factories

### Logging

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    afterBuild { user, _ ->
        println("Built user: name=${user.name}, email=${user.email}")
    }

    beforeCreate { user, _ ->
        println("Creating user: ${user.name}")
    }

    afterCreate { user, _ ->
        println("Created user: id=${user.id}, name=${user.name}")
    }
}
```

### Attribute Inspection

```kotlin
@Test
fun `inspect factory attributes`() {
    val attrs = dsl.factory<UserRecord>().attributes()

    println("Factory attributes:")
    attrs.forEach { (name, value) ->
        println("  $name: $value (${value?.javaClass?.simpleName})")
    }
}
```

### Sequence State

```kotlin
@Test
fun `check sequence state`() {
    dsl.factory<UserRecord>().build()
    dsl.factory<UserRecord>().build()
    dsl.factory<UserRecord>().build()

    // Sequences have incremented
    val attrs = dsl.factory<UserRecord>().attributes()
    println("Next user email: ${attrs["email"]}")  // user4@example.com
}
```

## Integration with Other Tools

### MockK

```kotlin
@Test
fun `factory with mocked service`() {
    val mockEmailService = mockk<EmailService>()
    every { mockEmailService.sendWelcome(any()) } returns Unit

    factory<UserRecord> {
        name = "User"

        afterCreate { user, _ ->
            mockEmailService.sendWelcome(user.email)
        }
    }

    val user = dsl.factory<UserRecord>().create()

    verify { mockEmailService.sendWelcome(user.email) }
}
```

### Faker

```kotlin
import com.github.javafaker.Faker

val faker = Faker()

factory<UserRecord> {
    attribute("name") {
        faker.name().fullName()
    }

    attribute("email") {
        faker.internet().emailAddress()
    }

    attribute("address") {
        faker.address().fullAddress()
    }
}

val user = dsl.factory<UserRecord>().build()
// Realistic fake data
```

### Cucumber

```kotlin
Given("a user exists") {
    dsl.factory<UserRecord>().create()
}

Given("a user {string} exists") { name: String ->
    dsl.factory<UserRecord>().create(mapOf(
        "name" to name
    ))
}

Given("{int} users exist") { count: Int ->
    dsl.factory<UserRecord>().createList(count)
}
```

## Best Practices Summary

1. **Keep factories simple** - One responsibility per factory
2. **Use traits for variations** - Don't create multiple similar factories
3. **Reset sequences** - Always reset in `@BeforeEach`
4. **Use transactions** - Rollback for fast, isolated tests
5. **Batch operations** - Use `createList()` for bulk data
6. **Document transients** - Explain what they control
7. **Validate early** - Use `beforeCreate` for validation
8. **Avoid circular dependencies** - Be careful with associations
9. **Use descriptive names** - Make intent clear
10. **Test your factories** - Ensure they work as expected

## Common Pitfalls

1. **Not resetting sequences** - Leads to flaky tests
2. **Circular associations** - Stack overflow
3. **Heavy callbacks** - Slow test execution
4. **Mixing transaction strategies** - Unpredictable behavior
5. **Hardcoded values** - Use sequences for uniqueness
6. **Too many traits** - Consider separate factories
7. **Side effects in `afterBuild`** - Keep it pure
8. **Modifying records in `afterCreate`** - Changes won't persist
9. **Not using batch operations** - Slow tests
10. **Forgetting transient defaults** - Null pointer exceptions

## Next Steps

- Review [examples/AdvancedExample.kt](../../examples/src/main/kotlin/com/example/faktory/examples/AdvancedExample.kt)
- Check integration tests in `src/test/kotlin/integration/`
- Read architecture docs in `docs/architecture/`
- Explore feature specs in `docs/features/`
