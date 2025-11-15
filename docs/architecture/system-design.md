# System Design

## 関連ドキュメント
- [Product Vision](../planning/product-vision.md) - 課題と解決方法
- [Core Interfaces](./core-interfaces.md) - コアインターフェース定義
- [jOOQ Integration](./jooq-integration.md) - jOOQ統合設計
- [Phase 1: Foundation](../implementation/phase1-foundation.md) - 基盤構築タスク

## アーキテクチャ概要

### レイヤー構造

```
┌─────────────────────────────────────┐
│         User Test Code              │
│  (TestクラスでFactoryを使用)         │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Factory DSL Layer              │
│  - ファクトリ定義                    │
│  - トレイト定義                      │
│  - シーケンス定義                    │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     Factory Registry Layer          │
│  - ファクトリ登録・検索              │
│  - 名前空間管理                      │
│  - 継承解決                          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Builder Layer                  │
│  - build/create戦略                 │
│  - アソシエーション解決              │
│  - コールバック実行                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      jOOQ Integration Layer         │
│  - Record生成                       │
│  - DB永続化                         │
│  - トランザクション管理              │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          Database                   │
└─────────────────────────────────────┘
```

### 依存関係の方向

- **上位レイヤー → 下位レイヤー**: 依存OK
- **下位レイヤー → 上位レイヤー**: 依存NG（インターフェース経由のみ）
- **同一レイヤー内**: 依存最小化

## コアコンポーネント

### 1. FactoryDefinition
**責務**: ファクトリの定義を保持
**依存**: なし
**提供先**: FactoryRegistry

```kotlin
interface FactoryDefinition<T : Record> {
    val recordClass: KClass<T>
    val attributes: Map<String, AttributeDefinition<*>>
    val traits: Map<String, TraitDefinition<T>>
    val callbacks: CallbackRegistry<T>
    val parent: FactoryDefinition<T>?
}
```

### 2. FactoryRegistry
**責務**: ファクトリの登録・検索・解決
**依存**: FactoryDefinition
**提供先**: FactoryBuilder

```kotlin
interface FactoryRegistry {
    fun <T : Record> register(name: String, definition: FactoryDefinition<T>)
    fun <T : Record> find(recordClass: KClass<T>, name: String? = null): FactoryDefinition<T>
    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T>
}
```

### 3. FactoryBuilder
**責務**: レコードの生成・永続化
**依存**: FactoryDefinition, SequenceManager, AssociationResolver
**提供先**: User Code

```kotlin
interface FactoryBuilder<T : Record> {
    fun build(attributes: Map<String, Any?> = emptyMap()): T
    fun create(attributes: Map<String, Any?> = emptyMap()): T
    fun buildList(count: Int, attributes: Map<String, Any?> = emptyMap()): List<T>
    fun createList(count: Int, attributes: Map<String, Any?> = emptyMap()): List<T>
}
```

### 4. SequenceManager
**責務**: シーケンス値の管理・生成
**依存**: なし
**提供先**: FactoryBuilder

```kotlin
interface SequenceManager {
    fun <T> next(name: String, generator: (Int) -> T): T
    fun reset(name: String? = null)
    fun current(name: String): Int
}
```

### 5. AssociationResolver
**責務**: 関連レコードの解決・生成
**依存**: FactoryRegistry, FactoryBuilder
**提供先**: FactoryBuilder

```kotlin
interface AssociationResolver {
    fun <T : Record> resolve(
        association: Association<T>,
        context: BuildContext
    ): T
}
```

### 6. CallbackRegistry
**責務**: コールバックの管理・実行
**依存**: なし
**提供先**: FactoryBuilder

```kotlin
interface CallbackRegistry<T : Record> {
    fun afterBuild(callback: (T) -> Unit)
    fun beforeCreate(callback: (T) -> Unit)
    fun afterCreate(callback: (T) -> Unit)
    fun execute(phase: CallbackPhase, record: T, transients: TransientContext)
}
```

### 7. TransactionManager
**責務**: トランザクションの管理
**依存**: DSLContext（jOOQ）
**提供先**: FactoryBuilder

```kotlin
interface TransactionManager {
    fun <T> withTransaction(block: () -> T): T
    fun <T> withRollback(block: () -> T): T
    fun begin()
    fun commit()
    fun rollback()
}
```

## データフロー

### build()の場合

```
User Code
  │
  ├─→ FactoryBuilder.build()
  │     │
  │     ├─→ FactoryRegistry.resolve()
  │     │     └─→ 継承チェーン解決
  │     │
  │     ├─→ 属性値の評価
  │     │     ├─→ SequenceManager.next()
  │     │     └─→ AssociationResolver.resolve()
  │     │
  │     ├─→ jOOQ Record生成
  │     │
  │     └─→ CallbackRegistry.execute(afterBuild)
  │
  └─→ Record返却
```

### create()の場合

```
User Code
  │
  ├─→ FactoryBuilder.create()
  │     │
  │     ├─→ [build()と同様]
  │     │
  │     ├─→ CallbackRegistry.execute(beforeCreate)
  │     │
  │     ├─→ DSLContext.executeInsert()
  │     │     └─→ Database
  │     │
  │     └─→ CallbackRegistry.execute(afterCreate)
  │
  └─→ Record返却（ID付き）
```

## エラーハンドリング戦略

### エラーの分類

| エラー種別 | 処理方針 | 例 |
|-----------|---------|---|
| 設定エラー | 即座に例外送出 | ファクトリ未登録、型不一致 |
| 循環参照 | 検出して例外送出 | A→B→Aの関連 |
| DB制約違反 | jOOQの例外を伝播 | ユニーク制約違反 |
| 一時的エラー | リトライ可能性を提供 | DB接続エラー |

### 例外階層

```kotlin
sealed class FactoryException : RuntimeException()

class FactoryNotFound(recordClass: KClass<*>) : FactoryException()
class CircularAssociation(chain: List<KClass<*>>) : FactoryException()
class InvalidAttribute(name: String, reason: String) : FactoryException()
class DatabaseConstraintViolation(cause: DataAccessException) : FactoryException()
```

## スレッドセーフティ

### 方針
- **FactoryRegistry**: スレッドセーフ（読み込み専用、初期化時のみ書き込み）
- **SequenceManager**: スレッドセーフ（AtomicIntegerで管理）
- **FactoryBuilder**: スレッドアンセーフ（テスト毎にインスタンス作成）

### 並列テスト対応
```kotlin
@TestInstance(Lifecycle.PER_METHOD)
class UserTest {
    private lateinit var sequenceManager: SequenceManager

    @BeforeEach
    fun setup() {
        sequenceManager = SequenceManager()
    }
}
```

## パフォーマンス考慮事項

### 最適化ポイント

1. **prepared statement再利用**
   - jOOQのbatch insert APIを活用
   - createList()で一括INSERT

2. **遅延評価**
   - アソシエーションは必要時のみ解決
   - 属性値の評価は遅延実行

3. **キャッシュ戦略**
   - FactoryDefinitionの解決結果をキャッシュ
   - シーケンス値の先読み

## 拡張ポイント

### プラグインアーキテクチャ

```kotlin
interface FactoryPlugin {
    fun onFactoryRegister(definition: FactoryDefinition<*>)
    fun onBuild(record: Record)
    fun onCreate(record: Record)
}

// 使用例: ロギングプラグイン
class LoggingPlugin : FactoryPlugin {
    override fun onCreate(record: Record) {
        logger.info("Created: ${record.javaClass.simpleName}")
    }
}
```

### カスタム戦略

```kotlin
interface BuildStrategy<T : Record> {
    fun execute(definition: FactoryDefinition<T>, attributes: Map<String, Any?>): T
}

// 使用例: buildStubbedの実装
class StubbedBuildStrategy<T : Record> : BuildStrategy<T> {
    override fun execute(definition: FactoryDefinition<T>, attributes: Map<String, Any?>): T {
        return mockk<T> {
            // モック設定
        }
    }
}
```

## 設計原則

1. **単一責任の原則**: 各コンポーネントは1つの責務のみ
2. **開放閉鎖の原則**: プラグインで拡張可能
3. **依存性逆転の原則**: インターフェースに依存
4. **インターフェース分離の原則**: 必要最小限のインターフェース
5. **疎結合**: レイヤー間は明確なインターフェース経由
