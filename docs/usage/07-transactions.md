# Transactions

Faktory Bot provides transaction management utilities for test isolation and rollback.

## Basic Transaction Management

### Automatic Rollback

Use `withFactoryTransaction` to automatically rollback changes:

```kotlin
import io.github.krhrtky.faktory.transaction.withFactoryTransaction

@Test
fun `user creation in transaction`() {
    withFactoryTransaction(dsl) {
        val user = dsl.factory<UserRecord>().create()
        assertThat(user.id).isNotNull()
    }

    // Transaction rolled back - no user in database
    val count = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
    assertThat(count).isEqualTo(0)
}
```

### Manual Transaction Control

```kotlin
import org.jooq.impl.DSL

@Test
fun `manual transaction control`() {
    dsl.transaction { config ->
        val ctx = DSL.using(config)

        val user = ctx.factory<UserRecord>().create()
        assertThat(user.id).isNotNull()

        // Explicit rollback
        throw RuntimeException("Rollback")
    }

    // Transaction rolled back
    val count = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
    assertThat(count).isEqualTo(0)
}
```

## JUnit 5 Extension

Use `FactoryTransactionExtension` for automatic transaction management per test:

```kotlin
import io.github.krhrtky.faktory.transaction.FactoryTransactionExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FactoryTransactionExtension::class)
class UserTest {
    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `test 1 - creates user`() {
        val user = dsl.factory<UserRecord>().create()
        assertThat(user.id).isNotNull()
    }
    // Transaction rolled back automatically

    @Test
    fun `test 2 - database is clean`() {
        val count = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)  // ✅ Previous test rolled back
    }
}
```

### How It Works

1. `@BeforeEach`: Starts transaction
2. Test executes with transaction active
3. `@AfterEach`: Rolls back transaction

### Benefits

- Automatic cleanup between tests
- Fast test execution (no DELETE operations)
- Test isolation guaranteed
- Database state preserved

## Spring @Transactional

Faktory Bot works with Spring's `@Transactional`:

```kotlin
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class UserServiceTest {
    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `user creation in Spring transaction`() {
        val user = dsl.factory<UserRecord>().create()
        assertThat(user.id).isNotNull()

        // Service method call
        userService.updateUser(user.id, "New Name")
    }
    // Transaction rolled back by Spring
}
```

### Rollback Rules

```kotlin
@Transactional(rollbackFor = [Exception::class])
@Test
fun `rollback on any exception`() {
    dsl.factory<UserRecord>().create()

    throw RuntimeException("Trigger rollback")
}
```

## Nested Transactions

jOOQ supports nested transactions with savepoints:

```kotlin
@Test
fun `nested transactions with savepoints`() {
    dsl.transaction { outerConfig ->
        val outerCtx = DSL.using(outerConfig)

        val user1 = outerCtx.factory<UserRecord>().create()

        try {
            outerCtx.transaction { innerConfig ->
                val innerCtx = DSL.using(innerConfig)

                val user2 = innerCtx.factory<UserRecord>().create()

                throw RuntimeException("Rollback inner")
            }
        } catch (e: RuntimeException) {
            // Inner transaction rolled back
        }

        // user1 still exists in outer transaction
        val count = outerCtx.selectCount().from(USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }
}
```

## Transaction Isolation Levels

### PostgreSQL

```kotlin
import java.sql.Connection

@Test
fun `read committed isolation`() {
    dsl.connection { conn ->
        conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED

        dsl.transaction { config ->
            val ctx = DSL.using(config)
            val user = ctx.factory<UserRecord>().create()
        }
    }
}
```

### Common Isolation Levels

| Level | Description | Use Case |
|-------|-------------|----------|
| READ_UNCOMMITTED | Dirty reads allowed | Not recommended |
| READ_COMMITTED | Default for PostgreSQL | Most tests |
| REPEATABLE_READ | No phantom reads | Sensitive operations |
| SERIALIZABLE | Full isolation | Critical operations |

## Deadlock Handling

### Automatic Retry

```kotlin
import io.github.krhrtky.faktory.transaction.DeadlockRetryTransactionManager

val txManager = DeadlockRetryTransactionManager(
    dsl = dsl,
    maxRetries = 3,
    retryDelayMs = 100
)

txManager.execute {
    dsl.factory<UserRecord>().create()
}
```

### Manual Retry Logic

```kotlin
@Test
fun `retry on deadlock`() {
    var attempts = 0
    val maxAttempts = 3

    while (attempts < maxAttempts) {
        try {
            dsl.transaction { config ->
                val ctx = DSL.using(config)
                ctx.factory<UserRecord>().create()
            }
            break
        } catch (e: DataAccessException) {
            if (isDeadlock(e) && attempts < maxAttempts - 1) {
                attempts++
                Thread.sleep(100)
            } else {
                throw e
            }
        }
    }
}

fun isDeadlock(e: Exception): Boolean {
    val message = e.message ?: ""
    return message.contains("deadlock") || message.contains("40P01")
}
```

## Test Cleanup Strategies

### 1. Transaction Rollback (Recommended)

```kotlin
@ExtendWith(FactoryTransactionExtension::class)
class UserTest {
    @Test
    fun test() {
        dsl.factory<UserRecord>().create()
    }
    // Automatic rollback
}
```

**Pros**: Fast, reliable, no manual cleanup
**Cons**: Doesn't test commit behavior

### 2. Manual Cleanup

```kotlin
@AfterEach
fun cleanup() {
    dsl.deleteFrom(POSTS).execute()
    dsl.deleteFrom(USERS).execute()
}
```

**Pros**: Tests actual commit behavior
**Cons**: Slow, order-dependent (foreign keys)

### 3. Truncate Tables

```kotlin
@AfterEach
fun cleanup() {
    dsl.truncate(POSTS).cascade().execute()
    dsl.truncate(USERS).cascade().execute()
}
```

**Pros**: Fast cleanup
**Cons**: Resets sequences, order-dependent

### 4. Database Recreation

```kotlin
@BeforeEach
fun setup() {
    recreateDatabase()
    runMigrations()
}
```

**Pros**: Clean state guaranteed
**Cons**: Very slow

## Best Practices

### 1. Use FactoryTransactionExtension

```kotlin
// ✅ Automatic rollback
@ExtendWith(FactoryTransactionExtension::class)
class UserTest {
    @Test
    fun test() {
        dsl.factory<UserRecord>().create()
    }
}

// ❌ Manual cleanup
class UserTest {
    @AfterEach
    fun cleanup() {
        dsl.deleteFrom(USERS).execute()
    }
}
```

### 2. Avoid @DirtiesContext

```kotlin
// ❌ Slow - recreates Spring context
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserTest { }

// ✅ Fast - transaction rollback
@SpringBootTest
@Transactional
class UserTest { }
```

### 3. Test Transaction Boundaries

```kotlin
@Test
fun `service method commits transaction`() {
    val userId = dsl.transaction { config ->
        val ctx = DSL.using(config)
        val user = ctx.factory<UserRecord>().create()
        user.id
    }

    // Verify commit worked
    val found = dsl.selectFrom(USERS)
        .where(USERS.ID.eq(userId))
        .fetchOne()

    assertThat(found).isNotNull()
}
```

### 4. Isolate Database Tests

```kotlin
// ✅ Each test is isolated
@ExtendWith(FactoryTransactionExtension::class)
class UserTest {
    @Test
    fun test1() {
        dsl.factory<UserRecord>().create()
        val count = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun test2() {
        // Clean database - test1 rolled back
        val count = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }
}
```

## Testcontainers Integration

Faktory Bot works seamlessly with Testcontainers:

```kotlin
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ExtendWith(FactoryTransactionExtension::class)
class UserIntegrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16")
            .apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
            }
    }

    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `create user in Testcontainer`() {
        val user = dsl.factory<UserRecord>().create()
        assertThat(user.id).isNotNull()
    }
    // Rolled back automatically
}
```

## Common Pitfalls

### Not Rolling Back in Tests

```kotlin
// ❌ Data leaks between tests
@Test
fun test1() {
    dsl.factory<UserRecord>().create()
}

@Test
fun test2() {
    val count = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
    assertThat(count).isEqualTo(0)  // ❌ Fails - test1 leaked data
}

// ✅ Use transaction extension
@ExtendWith(FactoryTransactionExtension::class)
class UserTest {
    @Test
    fun test1() { ... }

    @Test
    fun test2() { ... }
}
```

### Mixing Transaction Strategies

```kotlin
// ❌ Conflicting transaction management
@SpringBootTest
@Transactional
@ExtendWith(FactoryTransactionExtension::class)  // Don't mix!
class UserTest { }

// ✅ Choose one
@SpringBootTest
@Transactional
class UserTest { }
```

### Testing Async Operations

```kotlin
// ❌ Async operation may not see transaction
@Test
fun `async operation`() {
    dsl.transaction { config ->
        val ctx = DSL.using(config)
        val user = ctx.factory<UserRecord>().create()

        CompletableFuture.runAsync {
            // May not see 'user' - different transaction context
            userService.process(user.id)
        }.join()
    }
}

// ✅ Complete async in same transaction
@Test
fun `async operation with proper context`() {
    val user = dsl.factory<UserRecord>().create()

    CompletableFuture.runAsync {
        // Separate transaction - user is committed
        userService.process(user.id)
    }.join()
}
```

## Next Steps

- See [Advanced Topics](08-advanced.md) for complex scenarios
- Check integration test examples in `src/test/kotlin/integration/`
- Review [examples/](../../examples/) for working code
