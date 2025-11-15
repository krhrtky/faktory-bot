# Transactions

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P2
- [Build Strategies](./build-strategies.md) - create戦略（依存元）
- [jOOQ Integration](../architecture/jooq-integration.md) - jOOQトランザクション
- [Phase 6: Transactions](../implementation/phase6-transactions.md) - 実装タスク

## 依存関係
**依存元**: Build Strategies（create）
**依存先**: jOOQ Integration

## 概要

Transactionsは、テストデータの作成をトランザクション内で行い、テスト後に自動ロールバックする機能。テストの独立性を保証する。

### 設計目標
1. **テスト独立性**: テスト間のデータ汚染防止
2. **自動クリーンアップ**: ロールバックの自動化
3. **ネスト対応**: トランザクションのネスト
4. **柔軟性**: コミット/ロールバックの制御

## 基本的な使用例

### 自動ロールバック

```kotlin
@Test
fun testWithRollback() = withFactoryTransaction {
    val user = UserFactory.create()
    val posts = PostFactory.createList(10, userId = user.id)

    // テスト実行
    assertThat(posts).hasSize(10)

    // 自動的にロールバック（明示的なクリーンアップ不要）
}

@Test
fun anotherTest() {
    // 前のテストのデータは残っていない
    val users = userRepository.findAll()
    assertThat(users).isEmpty()
}
```

### JUnit5との統合

```kotlin
@TestInstance(Lifecycle.PER_METHOD)
class UserServiceTest {

    @BeforeEach
    fun setup() {
        TransactionManager.begin()
    }

    @AfterEach
    fun cleanup() {
        TransactionManager.rollback()
    }

    @Test
    fun `user can be created`() {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()
    }

    @Test
    fun `users are isolated between tests`() {
        val users = userRepository.findAll()
        assertThat(users).isEmpty()  // 前のテストの影響なし
    }
}
```

## TransactionManager実装

### インターフェース

```kotlin
interface TransactionManager {
    fun <T> withTransaction(block: () -> T): T
    fun <T> withRollback(block: () -> T): T

    fun begin()
    fun commit()
    fun rollback()

    fun setIsolationLevel(level: IsolationLevel)
    fun getCurrentTransaction(): Transaction?
}

enum class IsolationLevel {
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}
```

### jOOQ実装

```kotlin
class JooqTransactionManager(
    private val dsl: DSLContext
) : TransactionManager {

    private val currentTransaction = ThreadLocal<Transaction>()

    override fun <T> withTransaction(block: () -> T): T {
        return dsl.transactionResult { config ->
            val transactionalDsl = DSL.using(config)
            val transaction = Transaction(transactionalDsl)
            currentTransaction.set(transaction)

            try {
                block()
            } finally {
                currentTransaction.remove()
            }
        }
    }

    override fun <T> withRollback(block: () -> T): T {
        return dsl.transactionResult { config ->
            val transactionalDsl = DSL.using(config)
            val transaction = Transaction(transactionalDsl)
            currentTransaction.set(transaction)

            try {
                block()
                throw RollbackException()
            } catch (e: RollbackException) {
                null as T
            } finally {
                currentTransaction.remove()
            }
        }
    }

    override fun begin() {
        throw UnsupportedOperationException("Use withTransaction instead")
    }

    override fun commit() {
        throw UnsupportedOperationException("Managed by withTransaction")
    }

    override fun rollback() {
        throw UnsupportedOperationException("Managed by withTransaction")
    }

    override fun setIsolationLevel(level: IsolationLevel) {
        val sqlLevel = when (level) {
            IsolationLevel.READ_UNCOMMITTED -> Connection.TRANSACTION_READ_UNCOMMITTED
            IsolationLevel.READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED
            IsolationLevel.REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ
            IsolationLevel.SERIALIZABLE -> Connection.TRANSACTION_SERIALIZABLE
        }

        dsl.connection { conn ->
            conn.transactionIsolation = sqlLevel
        }
    }

    override fun getCurrentTransaction(): Transaction? {
        return currentTransaction.get()
    }

    private class RollbackException : RuntimeException()
}

data class Transaction(
    val dsl: DSLContext,
    val id: String = UUID.randomUUID().toString(),
    val startedAt: LocalDateTime = LocalDateTime.now()
)
```

## 高度な使用例

### ネストされたトランザクション

```kotlin
@Test
fun nestedTransactions() = withFactoryTransaction {
    val user = UserFactory.create()

    withFactoryTransaction {
        val post = PostFactory.create(userId = user.id)
        assertThat(post.userId).isEqualTo(user.id)
        // 内側のトランザクションもロールバック
    }

    // 外側のトランザクションもロールバック
}
```

### Savepoint

```kotlin
@Test
fun testWithSavepoint() = withFactoryTransaction { tx ->
    val user1 = UserFactory.create()

    tx.savepoint("after_user1")

    val user2 = UserFactory.create()
    val user3 = UserFactory.create()

    // user2, user3のみロールバック
    tx.rollbackToSavepoint("after_user1")

    val users = userRepository.findAll()
    assertThat(users).containsOnly(user1)
}
```

### 分離レベルの指定

```kotlin
@Test
fun testWithReadCommitted() {
    TransactionManager.setIsolationLevel(IsolationLevel.READ_COMMITTED)

    withFactoryTransaction {
        val user = UserFactory.create()

        // 別スレッドからは見えない（コミット前）
        assertThat(userRepository.findById(user.id)).isNotNull()
    }
}
```

### 明示的なコミット

```kotlin
@Test
fun testWithCommit() {
    withFactoryTransaction { tx ->
        val user = UserFactory.create()

        if (shouldKeepData) {
            tx.commit()  // 明示的にコミット
        }
        // それ以外はロールバック
    }
}
```

## Spring統合

### @Transactionalとの併用

```kotlin
@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    lateinit var userService: UserService

    @Test
    fun `user can be created`() {
        // @Transactionalにより自動ロールバック
        val user = UserFactory.create()

        userService.activate(user.id)

        assertThat(user.isActive).isTrue()
    }
}
```

### TestTransaction

```kotlin
@SpringBootTest
class UserServiceTest {

    @Autowired
    lateinit var testTransaction: TestTransaction

    @Test
    @Rollback
    fun `test with rollback`() {
        val user = UserFactory.create()

        // テスト実行

        // @Rollbackにより自動ロールバック
    }

    @Test
    @Commit
    fun `test with commit`() {
        val user = UserFactory.create()

        // テスト実行

        // @Commitにより永続化
    }
}
```

## パフォーマンス考慮事項

### トランザクション境界の最適化

```kotlin
// Bad: 細かすぎるトランザクション
@Test
fun inefficientTest() {
    withFactoryTransaction {
        val user1 = UserFactory.create()
    }

    withFactoryTransaction {
        val user2 = UserFactory.create()
    }

    withFactoryTransaction {
        val user3 = UserFactory.create()
    }
}

// Good: 適切な境界
@Test
fun efficientTest() = withFactoryTransaction {
    val user1 = UserFactory.create()
    val user2 = UserFactory.create()
    val user3 = UserFactory.create()
}
```

### コネクションプーリング

```kotlin
class PooledTransactionManager(
    private val dataSource: DataSource
) : TransactionManager {

    override fun <T> withTransaction(block: () -> T): T {
        return dataSource.connection.use { conn ->
            conn.autoCommit = false

            try {
                val result = block()
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }
}
```

## エラーハンドリング

### トランザクション失敗

```kotlin
@Test
fun `transaction rollback on error`() {
    try {
        withFactoryTransaction {
            val user = UserFactory.create()

            throw RuntimeException("Simulated error")
        }
    } catch (e: RuntimeException) {
        // トランザクションはロールバック済み
    }

    // データは残っていない
    val users = userRepository.findAll()
    assertThat(users).isEmpty()
}
```

### デッドロック対策

```kotlin
class DeadlockRetryTransactionManager(
    private val delegate: TransactionManager,
    private val maxRetries: Int = 3
) : TransactionManager by delegate {

    override fun <T> withTransaction(block: () -> T): T {
        var attempts = 0

        while (attempts < maxRetries) {
            try {
                return delegate.withTransaction(block)
            } catch (e: DeadlockException) {
                attempts++
                if (attempts >= maxRetries) throw e

                Thread.sleep(100 * attempts)  // Exponential backoff
            }
        }

        throw IllegalStateException("Unreachable")
    }
}
```

### タイムアウト

```kotlin
class TimeoutTransactionManager(
    private val delegate: TransactionManager,
    private val timeoutSeconds: Long = 30
) : TransactionManager by delegate {

    override fun <T> withTransaction(block: () -> T): T {
        return withTimeout(Duration.ofSeconds(timeoutSeconds)) {
            delegate.withTransaction(block)
        }
    }
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `transaction rollback works`() {
    withFactoryTransaction {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()
    }

    // ロールバック確認
    val users = userRepository.findAll()
    assertThat(users).isEmpty()
}
```

### ネストのテスト

```kotlin
@Test
fun `nested transactions rollback correctly`() {
    withFactoryTransaction {
        val user1 = UserFactory.create()

        withFactoryTransaction {
            val user2 = UserFactory.create()
        }

        // 内側もロールバック
        val users = userRepository.findAll()
        assertThat(users).containsOnly(user1)
    }

    // 外側もロールバック
    val users = userRepository.findAll()
    assertThat(users).isEmpty()
}
```

### Savepointのテスト

```kotlin
@Test
fun `savepoint allows partial rollback`() {
    withFactoryTransaction { tx ->
        val user1 = UserFactory.create()

        tx.savepoint("sp1")

        val user2 = UserFactory.create()

        tx.rollbackToSavepoint("sp1")

        val users = userRepository.findAll()
        assertThat(users).containsOnly(user1)
    }
}
```

## ベストプラクティス

### 1. テスト毎のロールバック

```kotlin
// Good: 各テストで自動ロールバック
@TestInstance(Lifecycle.PER_METHOD)
class UserTest {
    @BeforeEach
    fun setup() {
        TransactionManager.begin()
    }

    @AfterEach
    fun cleanup() {
        TransactionManager.rollback()
    }
}

// Bad: 手動クリーンアップ（忘れるリスク）
@Test
fun test() {
    val user = UserFactory.create()

    // テスト

    userRepository.delete(user.id)  // 忘れる可能性
}
```

### 2. 適切なトランザクション境界

```kotlin
// Good: テスト全体で1つのトランザクション
@Test
fun test() = withFactoryTransaction {
    val user = UserFactory.create()
    val posts = PostFactory.createList(10, userId = user.id)

    // テスト
}

// Bad: 細かすぎるトランザクション
@Test
fun test() {
    val user = withFactoryTransaction { UserFactory.create() }
    val posts = withFactoryTransaction { PostFactory.createList(10, userId = user.id) }
}
```

### 3. 分離レベルの明示

```kotlin
// Good: 必要に応じて分離レベルを指定
@Test
fun testConcurrency() {
    TransactionManager.setIsolationLevel(IsolationLevel.SERIALIZABLE)

    withFactoryTransaction {
        // 同時実行制御が必要なテスト
    }
}
```

## 制限事項

1. **JDBCのみ**: JDBCトランザクションのみ対応
2. **分散トランザクション**: XA非対応
3. **非同期処理**: CompletableFuture等は別トランザクション

## 今後の拡張

- **分散トランザクション**: XA対応
- **リアクティブ**: R2DBCトランザクション
- **自動リトライ**: デッドロック時の自動リトライ

## まとめ

Transactionsは以下を実現:
1. **テスト独立性**: 自動ロールバック
2. **クリーンアップ自動化**: 手動削除不要
3. **柔軟性**: ネスト、Savepoint
4. **安全性**: 分離レベル制御
