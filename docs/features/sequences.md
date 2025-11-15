# Sequences

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P0
- [Factory DSL](./factory-dsl.md) - ファクトリ定義（依存元）
- [Core Interfaces](../architecture/core-interfaces.md) - SequenceManager
- [Phase 3: Essentials](../implementation/phase3-essentials.md) - 実装タスク

## 依存関係
**依存元**: Factory DSL
**依存先**: なし（独立機能）

## 概要

Sequencesは、一意な値を自動生成する機能。メールアドレスやユーザーコードなど、ユニーク制約のある属性に使用。

### 設計目標
1. **一意性保証**: 必ず異なる値を生成
2. **スレッドセーフ**: 並列テスト対応
3. **カスタマイズ可能**: 任意の生成ロジック
4. **リセット可能**: テスト間のクリーンアップ

## 基本的な使用例

### グローバルシーケンス

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
}

// 生成結果
UserFactory.build().email // "user1@example.com"
UserFactory.build().email // "user2@example.com"
UserFactory.build().email // "user3@example.com"
```

### 名前付きシーケンス

```kotlin
factory<UserRecord> {
    email = sequence("user_email") { n -> "user${n}@example.com" }
}

factory<AdminRecord> {
    email = sequence("admin_email") { n -> "admin${n}@example.com" }
}

// 独立したカウンター
UserFactory.build().email  // "user1@example.com"
UserFactory.build().email  // "user2@example.com"
AdminFactory.build().email // "admin1@example.com"
```

## シーケンスの種類

### 1. 単純な連番

```kotlin
factory<UserRecord> {
    code = sequence { n -> n }
}

// 1, 2, 3, 4, ...
```

### 2. フォーマット済み文字列

```kotlin
factory<UserRecord> {
    code = sequence { n -> "USR${n.toString().padStart(5, '0')}" }
}

// USR00001, USR00002, USR00003, ...
```

### 3. 日付ベース

```kotlin
factory<UserRecord> {
    email = sequence { n ->
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        "user${date}_${n}@example.com"
    }
}

// user20250115_1@example.com, user20250115_2@example.com, ...
```

### 4. ランダム要素との組み合わせ

```kotlin
factory<UserRecord> {
    email = sequence { n ->
        val random = UUID.randomUUID().toString().take(8)
        "user${n}_${random}@example.com"
    }
}

// user1_a3b4c5d6@example.com, user2_e7f8g9h0@example.com, ...
```

### 5. 複雑なビジネスロジック

```kotlin
factory<OrderRecord> {
    orderNo = sequence("order") { n ->
        val year = LocalDate.now().year
        val month = LocalDate.now().monthValue
        "$year${month.toString().padStart(2, '0')}-${n.toString().padStart(6, '0')}"
    }
}

// 202501-000001, 202501-000002, ...
```

## SequenceManager実装

### インターフェース

```kotlin
interface SequenceManager {
    fun <T> next(name: String, generator: (Int) -> T): T
    fun current(name: String): Int
    fun reset(name: String)
    fun resetAll()
}
```

### 実装

```kotlin
class DefaultSequenceManager : SequenceManager {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>()

    override fun <T> next(name: String, generator: (Int) -> T): T {
        val counter = sequences.computeIfAbsent(name) { AtomicInteger(0) }
        val value = counter.incrementAndGet()
        return generator(value)
    }

    override fun current(name: String): Int {
        return sequences[name]?.get() ?: 0
    }

    override fun reset(name: String) {
        sequences[name]?.set(0)
    }

    override fun resetAll() {
        sequences.clear()
    }
}
```

### シングルトン管理

```kotlin
object GlobalSequenceManager {
    private val instance = DefaultSequenceManager()

    fun getInstance(): SequenceManager = instance

    fun reset() {
        instance.resetAll()
    }
}
```

## スレッドセーフティ

### AtomicIntegerによる同期

```kotlin
private val counter = AtomicInteger(0)

fun next(): Int {
    return counter.incrementAndGet()  // アトミックな操作
}
```

### 並列テストでの使用

```kotlin
@TestInstance(Lifecycle.PER_METHOD)
class ParallelTest {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    fun test1() {
        val users = (1..100).map { UserFactory.create() }
        assertThat(users.map { it.email }).doesNotHaveDuplicates()
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    fun test2() {
        val users = (1..100).map { UserFactory.create() }
        assertThat(users.map { it.email }).doesNotHaveDuplicates()
    }
}
```

## シーケンスのリセット

### テスト毎のリセット

```kotlin
@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()
}

@Test
fun test1() {
    val user = UserFactory.build()
    assertThat(user.email).isEqualTo("user1@example.com")
}

@Test
fun test2() {
    val user = UserFactory.build()
    assertThat(user.email).isEqualTo("user1@example.com")  // リセットされている
}
```

### 特定シーケンスのみリセット

```kotlin
@Test
fun testSpecificReset() {
    GlobalSequenceManager.getInstance().reset("user_email")

    val user = UserFactory.build()
    assertThat(user.email).isEqualTo("user1@example.com")
}
```

### クラス単位でのリセット

```kotlin
@TestInstance(Lifecycle.PER_CLASS)
class UserTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupClass() {
            GlobalSequenceManager.reset()
        }
    }
}
```

## 高度な使用例

### 条件付きシーケンス

```kotlin
factory<UserRecord> {
    email = sequence { n ->
        if (n % 2 == 0) {
            "even${n}@example.com"
        } else {
            "odd${n}@example.com"
        }
    }
}
```

### 外部データソースとの連携

```kotlin
factory<UserRecord> {
    email = sequence("user_email") { n ->
        val domain = listOf("example.com", "test.com", "demo.com")[n % 3]
        "user${n}@${domain}"
    }
}
```

### 複数フィールドでの共有シーケンス

```kotlin
factory<UserRecord> {
    val userSeq = sequence("user")

    code = { n -> "USR${userSeq(n)}" }
    email = { n -> "user${userSeq(n)}@example.com" }
}
```

## エラーハンドリング

### オーバーフロー対策

```kotlin
class SafeSequenceManager : SequenceManager {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>()
    private val maxValue = Int.MAX_VALUE - 1000  // バッファを持つ

    override fun <T> next(name: String, generator: (Int) -> T): T {
        val counter = sequences.computeIfAbsent(name) { AtomicInteger(0) }
        val value = counter.incrementAndGet()

        if (value > maxValue) {
            throw SequenceOverflowException(name, value)
        }

        return generator(value)
    }
}

class SequenceOverflowException(
    name: String,
    value: Int
) : RuntimeException("Sequence '$name' overflowed at $value")
```

### リトライロジック

```kotlin
fun <T> retryOnConstraintViolation(block: () -> T): T {
    var attempts = 0
    while (attempts < 3) {
        try {
            return block()
        } catch (e: DataIntegrityViolationException) {
            attempts++
            if (attempts >= 3) throw e
        }
    }
    throw IllegalStateException("Unreachable")
}

// 使用
val user = retryOnConstraintViolation {
    UserFactory.create()
}
```

## パフォーマンス考慮事項

### カウンターのキャッシュ

```kotlin
class CachedSequenceManager : SequenceManager {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>()
    private val cache = ConcurrentHashMap<String, Int>()

    override fun current(name: String): Int {
        return cache.getOrPut(name) {
            sequences[name]?.get() ?: 0
        }
    }
}
```

### 一括予約

```kotlin
class BatchSequenceManager : SequenceManager {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>()

    fun reserve(name: String, count: Int): IntRange {
        val counter = sequences.computeIfAbsent(name) { AtomicInteger(0) }
        val start = counter.getAndAdd(count)
        return (start + 1)..(start + count)
    }
}

// 使用
val range = sequenceManager.reserve("user", 1000)
val users = range.map { n ->
    UserFactory.build(email = "user${n}@example.com")
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `sequence generates unique values`() {
    factory<UserRecord> {
        email = sequence { n -> "user${n}@example.com" }
    }

    val user1 = UserFactory.build()
    val user2 = UserFactory.build()
    val user3 = UserFactory.build()

    assertThat(setOf(user1.email, user2.email, user3.email)).hasSize(3)
}
```

### リセットのテスト

```kotlin
@Test
fun `reset clears sequence counter`() {
    factory<UserRecord> {
        email = sequence("test_email") { n -> "user${n}@example.com" }
    }

    UserFactory.build()  // user1@example.com
    UserFactory.build()  // user2@example.com

    GlobalSequenceManager.getInstance().reset("test_email")

    val user = UserFactory.build()
    assertThat(user.email).isEqualTo("user1@example.com")
}
```

### 並列実行のテスト

```kotlin
@Test
fun `sequence is thread-safe`() {
    factory<UserRecord> {
        email = sequence { n -> "user${n}@example.com" }
    }

    val users = (1..1000).toList().parallelStream()
        .map { UserFactory.build() }
        .collect(Collectors.toList())

    val uniqueEmails = users.map { it.email }.toSet()
    assertThat(uniqueEmails).hasSize(1000)
}
```

## ベストプラクティス

### 1. 名前付きシーケンスの使用

```kotlin
// Good: 明示的な名前
factory<UserRecord> {
    email = sequence("user_email") { n -> "user${n}@example.com" }
}

// Bad: 自動生成された名前（予測困難）
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }
}
```

### 2. リセット戦略の明確化

```kotlin
@TestInstance(Lifecycle.PER_METHOD)
class UserTest {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()  // テスト毎にリセット
    }
}
```

### 3. フォーマットの統一

```kotlin
// Good: 一貫したフォーマット
sequence { n -> "USR${n.toString().padStart(5, '0')}" }

// Bad: 不統一なフォーマット
sequence { n -> if (n < 10) "USR0${n}" else "USR${n}" }
```

## 制限事項

1. **Int範囲**: 最大2,147,483,647まで
2. **リセット**: 手動リセットが必要
3. **永続化**: シーケンス状態は保存されない

## 今後の拡張

- **Long対応**: より大きな値の範囲
- **データベースシーケンス連携**: DBのSEQUENCEと同期
- **自動リセット**: テストフレームワーク統合

## まとめ

Sequencesは以下を実現:
1. **一意性**: 重複のない値を自動生成
2. **スレッドセーフ**: 並列テスト対応
3. **柔軟性**: カスタム生成ロジック
4. **制御可能**: リセット機能
