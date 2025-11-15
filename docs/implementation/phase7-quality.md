# Phase 7: Quality

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Phase 6: Transactions](./phase6-transactions.md) - 前提Phase

## 期間
2週間

## 優先度
P0

## 目標
品質を保証し、リリース可能な状態にする。

## 前提条件
Phase 6完了

## 成果物
- テストスイート（90%以上カバレッジ）
- ドキュメント一式
- バグ修正

## タスク一覧

### 1. テスト実装

#### 1.1 単体テスト（90%以上カバレッジ）

**対象モジュール**:
- FactoryRegistry
- FactoryBuilder
- SequenceManager
- AssociationResolver
- CallbackRegistry
- TraitDefinition
- TransactionManager

**テスト例**:
```kotlin
class SequenceManagerTest {

    private lateinit var sequenceManager: SequenceManager

    @BeforeEach
    fun setup() {
        sequenceManager = DefaultSequenceManager()
    }

    @Test
    fun `next generates unique values`() {
        val values = (1..100).map {
            sequenceManager.next("test") { it }
        }

        assertThat(values).doesNotHaveDuplicates()
    }

    @Test
    fun `reset clears counter`() {
        sequenceManager.next("test") { it }
        sequenceManager.next("test") { it }

        sequenceManager.reset("test")

        val value = sequenceManager.next("test") { it }
        assertThat(value).isEqualTo(1)
    }

    @Test
    fun `concurrent access is thread-safe`() {
        val values = (1..1000).toList().parallelStream()
            .map { sequenceManager.next("test") { it } }
            .collect(Collectors.toList())

        assertThat(values.toSet()).hasSize(1000)
    }
}
```

#### 1.2 統合テスト（PostgreSQL, MySQL, H2）

**PostgreSQL**:
```kotlin
@SpringBootTest
@Testcontainers
class PostgreSQLIntegrationTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16")

    @Test
    fun `factory works with PostgreSQL`() {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()

        val found = userRepository.findById(user.id)
        assertThat(found).isNotNull()
    }
}
```

**MySQL**:
```kotlin
@SpringBootTest
@Testcontainers
class MySQLIntegrationTest {

    @Container
    val mysql = MySQLContainer<Nothing>("mysql:8")

    @Test
    fun `factory works with MySQL`() {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()

        val found = userRepository.findById(user.id)
        assertThat(found).isNotNull()
    }
}
```

**H2**:
```kotlin
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class H2IntegrationTest {

    @Test
    fun `factory works with H2`() {
        val user = UserFactory.create()
        assertThat(user.id).isNotNull()

        val found = userRepository.findById(user.id)
        assertThat(found).isNotNull()
    }
}
```

#### 1.3 パフォーマンステスト

```kotlin
class PerformanceTest {

    @Test
    fun `createBatch is faster than individual creates`() {
        val individualTime = measureTime {
            (1..100).forEach { UserFactory.create() }
        }

        val batchTime = measureTime {
            UserFactory.createBatch(100)
        }

        assertThat(batchTime).isLessThan(individualTime / 5)
    }

    @Test
    fun `buildStubbed is faster than build`() {
        val buildTime = measureTime {
            (1..1000).forEach { UserFactory.build() }
        }

        val stubbedTime = measureTime {
            (1..1000).forEach { UserFactory.buildStubbed() }
        }

        assertThat(stubbedTime).isLessThan(buildTime / 2)
    }
}
```

#### 1.4 並行実行テスト

```kotlin
class ConcurrencyTest {

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    fun `sequence is thread-safe in parallel tests`() {
        val users = (1..100).map { UserFactory.create() }

        assertThat(users.map { it.email }).doesNotHaveDuplicates()
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    fun `transaction isolation works`() {
        withFactoryTransaction {
            val user = UserFactory.create()
            assertThat(user.id).isNotNull()
        }

        val users = userRepository.findAll()
        assertThat(users).isEmpty()
    }
}
```

### 2. ドキュメント作成

#### 2.1 README.md
```markdown
# Faktory Bot

Type-safe test data factory for jOOQ and Kotlin.

## Features

- 🔒 **Type-safe**: Compile-time type checking
- 🚀 **Fast**: Batch insert optimization
- 🧩 **Flexible**: Traits, callbacks, inheritance
- 🔄 **Test isolation**: Automatic rollback

## Quick Start

```kotlin
// Define factory
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

// Use in tests
val user = UserFactory.create()
assertThat(user.email).matches("user\\d+@example.com")
```

## Installation

```kotlin
dependencies {
    testImplementation("com.example:faktory-bot:0.1.0")
}
```

## Documentation

See [docs/](docs/) for detailed documentation.
```

#### 2.2 APIドキュメント（KDoc）
```kotlin
/**
 * ファクトリを定義するDSL関数.
 *
 * @param T レコード型
 * @param name ファクトリ名（省略時は型名）
 * @param block ファクトリ定義ブロック
 * @return 定義されたファクトリ
 *
 * @sample
 * ```kotlin
 * factory<UserRecord> {
 *     name = "Default User"
 *     email = sequence { n -> "user${n}@example.com" }
 * }
 * ```
 */
inline fun <reified T : Record> factory(
    name: String? = null,
    noinline block: FactoryDslBuilder<T>.() -> Unit
): FactoryDefinition<T>
```

#### 2.3 使用例集
- 基本的な使用例
- トレイトの使用例
- アソシエーションの使用例
- トランザクションの使用例
- Spring統合例

#### 2.4 マイグレーションガイド
Factory Botからの移行手順を記載。

### 3. バグ修正

#### 3.1 バグトラッキング
GitHub Issuesでバグ管理:
- 優先度ラベル（P0, P1, P2）
- カテゴリラベル（bug, enhancement, documentation）
- マイルストーン（v0.1.0, v1.0.0）

#### 3.2 修正プロセス
1. バグ再現テストを作成
2. 修正実装
3. 修正確認
4. リグレッションテスト追加

## カバレッジ目標

| モジュール | 目標カバレッジ |
|-----------|---------------|
| core | 95% |
| dsl | 90% |
| builder | 95% |
| registry | 95% |
| sequence | 95% |
| association | 90% |
| transaction | 90% |
| **全体** | **90%** |

## 品質ゲート

以下の条件を全て満たすこと:
- ✅ テストカバレッジ90%以上
- ✅ 全ての単体テストが成功
- ✅ 全ての統合テスト（PostgreSQL, MySQL, H2）が成功
- ✅ パフォーマンステストが目標を達成
- ✅ 並行実行テストが成功
- ✅ KDocが全てのpublic APIに記載
- ✅ README.mdが完成
- ✅ 既知のP0バグがゼロ

## チェックリスト

### テスト
- [ ] 単体テスト実装（90%以上）
- [ ] PostgreSQL統合テスト
- [ ] MySQL統合テスト
- [ ] H2統合テスト
- [ ] パフォーマンステスト
- [ ] 並行実行テスト
- [ ] カバレッジ確認

### ドキュメント
- [ ] README.md作成
- [ ] KDoc記載
- [ ] 使用例集作成
- [ ] マイグレーションガイド作成

### 品質
- [ ] P0バグ修正
- [ ] P1バグ修正
- [ ] コードレビュー
- [ ] 静的解析（ktlint, detekt）
- [ ] 品質ゲートクリア

## 成功基準

- ✅ 全ての品質ゲートをクリア
- ✅ リリース可能な状態
- ✅ ドキュメントが完備

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| カバレッジ不足 | 高 | 早期のカバレッジ確認 |
| バグの発見 | 中 | バグ修正期間の確保 |
| ドキュメント遅延 | 中 | 並行してドキュメント作成 |

## 次のPhase

[Phase 8: Release](./phase8-release.md) - リリース準備
