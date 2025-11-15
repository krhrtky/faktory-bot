# Phase 6: Transactions

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Transactions](../features/transactions.md) - トランザクション機能
- [jOOQ Integration](../architecture/jooq-integration.md) - jOOQトランザクション
- [Phase 5: Performance](./phase5-performance.md) - 前提Phase

## 期間
1週間

## 優先度
P2

## 目標
トランザクション管理機能を実装し、テストの独立性を保証する。

## 前提条件
Phase 2完了（create()が動作すること）

## 成果物
- 自動ロールバック機能
- トランザクション境界設定
- ネストされたトランザクション対応

## タスク一覧

### 1. 基本的なトランザクション

#### 1.1 自動ロールバック機能
```kotlin
class JooqTransactionManager(
    private val dsl: DSLContext
) : TransactionManager {

    override fun <T> withRollback(block: () -> T): T {
        return dsl.transactionResult { config ->
            val transactionalDsl = DSL.using(config)

            try {
                val result = block()
                throw RollbackException()
            } catch (e: RollbackException) {
                null as T
            }
        }
    }

    private class RollbackException : RuntimeException()
}
```

#### 1.2 トランザクション境界の設定
```kotlin
fun <T> withFactoryTransaction(block: () -> T): T {
    return TransactionManager.getInstance().withRollback(block)
}

// 使用例
@Test
fun test() = withFactoryTransaction {
    val user = UserFactory.create()
    val posts = PostFactory.createList(10, userId = user.id)

    assertThat(posts).hasSize(10)

    // 自動的にロールバック
}
```

#### 1.3 分離レベル設定
```kotlin
class IsolationLevelTransactionManager(
    private val dsl: DSLContext
) : TransactionManager {

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
}
```

### 2. 高度なトランザクション

#### 2.1 ネストされたトランザクション
```kotlin
class NestedTransactionManager(
    private val dsl: DSLContext
) : TransactionManager {

    private val transactionStack = ThreadLocal.withInitial { mutableListOf<Transaction>() }

    override fun <T> withTransaction(block: () -> T): T {
        val stack = transactionStack.get()
        val isNested = stack.isNotEmpty()

        return if (isNested) {
            // ネストされたトランザクション
            withNestedTransaction(block)
        } else {
            // 最上位トランザクション
            withTopLevelTransaction(block)
        }
    }

    private fun <T> withTopLevelTransaction(block: () -> T): T {
        return dsl.transactionResult { config ->
            val tx = Transaction(DSL.using(config))
            transactionStack.get().add(tx)

            try {
                block()
            } finally {
                transactionStack.get().removeLast()
            }
        }
    }

    private fun <T> withNestedTransaction(block: () -> T): T {
        val currentTx = transactionStack.get().last()
        val savepointName = "sp_${UUID.randomUUID()}"

        currentTx.dsl.connection { conn ->
            conn.setSavepoint(savepointName)
        }

        return try {
            block()
        } catch (e: Exception) {
            currentTx.dsl.connection { conn ->
                conn.rollback(conn.getSavepoint(savepointName))
            }
            throw e
        }
    }
}
```

#### 2.2 Savepoint管理
```kotlin
data class Transaction(
    val dsl: DSLContext,
    val id: String = UUID.randomUUID().toString()
) {
    private val savepoints = mutableMapOf<String, Savepoint>()

    fun savepoint(name: String) {
        dsl.connection { conn ->
            savepoints[name] = conn.setSavepoint(name)
        }
    }

    fun rollbackToSavepoint(name: String) {
        val savepoint = savepoints[name]
            ?: throw IllegalArgumentException("Savepoint not found: $name")

        dsl.connection { conn ->
            conn.rollback(savepoint)
        }
    }

    fun releaseSavepoint(name: String) {
        val savepoint = savepoints.remove(name)
            ?: throw IllegalArgumentException("Savepoint not found: $name")

        dsl.connection { conn ->
            conn.releaseSavepoint(savepoint)
        }
    }
}
```

#### 2.3 デッドロック対策
```kotlin
class DeadlockRetryTransactionManager(
    private val delegate: TransactionManager,
    private val maxRetries: Int = 3,
    private val baseDelay: Long = 100
) : TransactionManager by delegate {

    override fun <T> withTransaction(block: () -> T): T {
        var attempts = 0

        while (attempts < maxRetries) {
            try {
                return delegate.withTransaction(block)
            } catch (e: Exception) {
                if (!isDeadlock(e) || attempts >= maxRetries - 1) {
                    throw e
                }

                attempts++
                val delay = baseDelay * (1 shl attempts)  // Exponential backoff
                Thread.sleep(delay)
            }
        }

        throw IllegalStateException("Unreachable")
    }

    private fun isDeadlock(e: Exception): Boolean {
        return e.message?.contains("deadlock", ignoreCase = true) == true
    }
}
```

## JUnit統合

### JUnit5 Extension
```kotlin
class FactoryTransactionExtension : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        TransactionManager.getInstance().begin()
    }

    override fun afterEach(context: ExtensionContext) {
        TransactionManager.getInstance().rollback()
    }
}

// 使用例
@ExtendWith(FactoryTransactionExtension::class)
class UserTest {

    @Test
    fun test1() {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()
    }

    @Test
    fun test2() {
        val users = userRepository.findAll()
        assertThat(users).isEmpty()  // test1の影響なし
    }
}
```

### Spring統合
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

## テスト実装

### トランザクションのテスト
```kotlin
@Test
fun `transaction rollback works`() {
    withFactoryTransaction {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()
    }

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

        val users = userRepository.findAll()
        assertThat(users).containsOnly(user1)
    }

    val users = userRepository.findAll()
    assertThat(users).isEmpty()
}
```

## チェックリスト

- [ ] 自動ロールバック実装
- [ ] トランザクション境界設定実装
- [ ] 分離レベル設定実装
- [ ] ネストされたトランザクション実装
- [ ] Savepoint管理実装
- [ ] デッドロック対策実装
- [ ] JUnit5 Extension実装
- [ ] Spring統合実装
- [ ] テストカバレッジ90%以上

## 成功基準

- ✅ トランザクションが正常に動作
- ✅ 自動ロールバックが機能
- ✅ ネストされたトランザクションが正常動作
- ✅ テストの独立性が保証される

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| トランザクション漏れ | 高 | JUnit Extension推奨 |
| デッドロック | 中 | リトライ機構実装 |
| ネストの複雑さ | 中 | Savepoint活用 |

## 次のPhase

[Phase 7: Quality](./phase7-quality.md) - 品質保証
