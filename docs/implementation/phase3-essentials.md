# Phase 3: Essentials

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Sequences](../features/sequences.md) - シーケンス機能
- [Associations](../features/associations.md) - アソシエーション
- [Transients](../features/transients.md) - Transient属性
- [Phase 2: Core](./phase2-core.md) - 前提Phase

## 期間
2週間

## 優先度
P0-P1

## 目標
ファクトリの必須機能（シーケンス、アソシエーション、Transient）を実装する。

## 前提条件
Phase 2完了

## 成果物
- シーケンス機能実装
- アソシエーション機能実装
- Transient属性実装
- 統合テスト

## タスク一覧

### 1. シーケンス機能

#### 1.1 グローバルシーケンスカウンター
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

#### 1.2 名前付きシーケンス
```kotlin
data class SequenceAttribute<T>(
    val name: String?,
    val generator: (Int) -> T
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext): T {
        val sequenceManager = context.sequenceManager
        val sequenceName = name ?: context.attributeName
        return sequenceManager.next(sequenceName, generator)
    }
}
```

#### 1.3 スレッドセーフな実装
```kotlin
@Test
fun `sequence is thread-safe`() {
    val sequenceManager = DefaultSequenceManager()

    val results = (1..1000).toList().parallelStream()
        .map { sequenceManager.next("test") { it } }
        .collect(Collectors.toList())

    assertThat(results.toSet()).hasSize(1000)
}
```

#### 1.4 リセット機能
```kotlin
object GlobalSequenceManager {
    private val instance = DefaultSequenceManager()

    fun getInstance(): SequenceManager = instance

    fun reset() {
        instance.resetAll()
    }
}

// テストでの使用
@BeforeEach
fun setup() {
    GlobalSequenceManager.reset()
}
```

### 2. アソシエーション

#### 2.1 基本的な関連付け
```kotlin
data class AssociationAttribute<T : Record>(
    val targetClass: KClass<T>,
    val factoryName: String? = null,
    val overrides: Map<String, Any?> = emptyMap()
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext): T {
        return context.associationResolver.resolve(this, context)
    }
}
```

#### 2.2 遅延評価メカニズム
```kotlin
class DefaultAssociationResolver(
    private val factoryRegistry: FactoryRegistry
) : AssociationResolver {

    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        val factory = factoryRegistry.find(
            association.targetClass,
            association.factoryName
        )

        val builder = DefaultFactoryBuilder(
            factory,
            context.dsl,
            context.sequenceManager,
            this
        )

        val newContext = context.withDepth(context.depth + 1)

        return if (context.isCreate) {
            builder.create(association.overrides)
        } else {
            builder.build(association.overrides)
        }
    }
}
```

#### 2.3 循環参照検出・解決
```kotlin
class CircularDependencyDetector {
    private val stack = ThreadLocal.withInitial { mutableListOf<KClass<*>>() }

    fun <T> withCheck(recordClass: KClass<*>, block: () -> T): T {
        val currentStack = stack.get()

        if (recordClass in currentStack) {
            throw CircularAssociationException(currentStack + recordClass)
        }

        currentStack.add(recordClass)
        try {
            return block()
        } finally {
            currentStack.removeLast()
        }
    }
}
```

#### 2.4 カスケード操作
```kotlin
class CascadingAssociationResolver(
    private val delegate: AssociationResolver
) : AssociationResolver by delegate {

    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        val record = delegate.resolve(association, context)

        if (context.isCreate) {
            updateForeignKey(context.currentRecord, record)
        }

        return record
    }

    private fun <T : Record> updateForeignKey(
        record: Record,
        associatedRecord: T
    ) {
        // 外部キーの自動設定
        val table = JooqTableResolver.resolveTable(record::class)
        val foreignKeys = ForeignKeyResolver.resolveForeignKeys(table)

        foreignKeys.forEach { fk ->
            if (fk.targetTable == JooqTableResolver.resolveTable(associatedRecord::class)) {
                val id = associatedRecord.getId()
                record.set(fk.sourceField as Field<Long>, id)
            }
        }
    }
}
```

### 3. Transient属性

#### 3.1 Transientコンテキスト実装
```kotlin
data class TransientDefinition(
    val properties: Map<String, Any?> = emptyMap()
) {
    inline fun <reified T> get(key: String): T? = properties[key] as? T

    fun with(key: String, value: Any?) = copy(
        properties = properties + (key to value)
    )

    fun merge(other: TransientDefinition) = TransientDefinition(
        properties = properties + other.properties
    )
}

data class TransientContext(
    private val values: Map<String, Any?> = emptyMap()
) {
    inline operator fun <reified T> get(key: String): T {
        return values[key] as? T
            ?: throw TransientNotFoundException(key)
    }

    fun with(key: String, value: Any?) = TransientContext(
        values + (key to value)
    )
}
```

#### 3.2 評価時の値渡し
```kotlin
class TransientEvaluator<T : Record> {
    fun evaluate(
        definition: FactoryDefinition<T>,
        overrides: Map<String, Any?>
    ): TransientContext {
        val baseValues = definition.transients.properties
        val mergedValues = baseValues + overrides.filterKeys { it in baseValues }

        return TransientContext(mergedValues)
    }
}
```

#### 3.3 コールバックとの統合
```kotlin
override fun create(overrides: Map<String, Any?>): T {
    val resolved = GlobalFactoryRegistry.resolve(definition)
    val transients = TransientEvaluator<T>().evaluate(definition, overrides)

    // ...

    resolved.mergedCallbacks.execute(
        CallbackPhase.BEFORE_CREATE,
        record,
        transients
    )

    record.store()

    resolved.mergedCallbacks.execute(
        CallbackPhase.AFTER_CREATE,
        record,
        transients
    )

    return record
}
```

## テスト実装

### 統合テスト例

```kotlin
@SpringBootTest
@Testcontainers
class FactoryIntegrationTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16")

    @Autowired
    lateinit var dsl: DSLContext

    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
    }

    @Test
    fun `factory with sequence and association works`() {
        factory<UserRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
        }

        factory<PostRecord> {
            title = "Post"
            user = association<UserRecord>()
        }

        val post = PostFactory.create()

        assertThat(post.userId).isNotNull()
        assertThat(post.user.email).matches("user\\d+@example.com")
    }

    @Test
    fun `transients control callback behavior`() {
        factory<UserRecord> {
            name = "User"

            transient {
                val postsCount = 5
            }

            afterCreate { user, transients ->
                PostFactory.createList(transients.postsCount, userId = user.id)
            }
        }

        val user = UserFactory.create(postsCount = 10)

        val posts = postRepository.findByUserId(user.id)
        assertThat(posts).hasSize(10)
    }
}
```

## チェックリスト

- [ ] グローバルシーケンス実装
- [ ] 名前付きシーケンス実装
- [ ] スレッドセーフ確認
- [ ] リセット機能実装
- [ ] 基本的なアソシエーション実装
- [ ] 遅延評価メカニズム実装
- [ ] 循環参照検出実装
- [ ] カスケード操作実装
- [ ] TransientDefinition実装
- [ ] TransientContext実装
- [ ] コールバック統合
- [ ] 統合テスト（PostgreSQL, MySQL, H2）

## 成功基準

- ✅ シーケンスが正常に動作
- ✅ アソシエーションが正常に動作
- ✅ Transientが正常に動作
- ✅ 統合テストが全て成功
- ✅ テストカバレッジ90%以上

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| 循環参照の検出漏れ | 高 | 徹底的なテスト |
| パフォーマンス劣化 | 中 | ベンチマーク実施 |
| スレッドセーフティの問題 | 高 | 並行テストの実施 |

## 次のPhase

[Phase 4: Extensions](./phase4-extensions.md) - 拡張機能実装
