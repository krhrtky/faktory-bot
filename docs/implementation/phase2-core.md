# Phase 2: Core

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Factory DSL](../features/factory-dsl.md) - DSL機能
- [Build Strategies](../features/build-strategies.md) - ビルド戦略
- [Core Interfaces](../architecture/core-interfaces.md) - インターフェース
- [Phase 1: Foundation](./phase1-foundation.md) - 前提Phase

## 期間
2週間

## 優先度
P0（必須）

## 目標
ファクトリの核となる機能（レジストリ、DSL、ビルド戦略）を実装する。

## 前提条件
Phase 1完了

## 成果物
- FactoryRegistry実装
- Factory DSL実装
- build()/create()メソッド実装
- 基本的なテストスイート

## タスク一覧

### 1. ファクトリレジストリ実装

#### 1.1 FactoryRegistry実装
```kotlin
class DefaultFactoryRegistry : FactoryRegistry {
    private val factories = ConcurrentHashMap<RegistryKey, FactoryDefinition<*>>()

    override fun <T : Record> register(definition: FactoryDefinition<T>) {
        val key = RegistryKey(definition.recordClass, definition.name)
        factories[key] = definition
    }

    override fun <T : Record> find(recordClass: KClass<T>): FactoryDefinition<T> {
        return find(recordClass, null)
    }

    override fun <T : Record> find(
        recordClass: KClass<T>,
        name: String?
    ): FactoryDefinition<T> {
        val key = RegistryKey(recordClass, name)
        @Suppress("UNCHECKED_CAST")
        return factories[key] as? FactoryDefinition<T>
            ?: throw FactoryNotFoundException(recordClass, name)
    }

    override fun <T : Record> resolve(
        definition: FactoryDefinition<T>
    ): ResolvedFactory<T> {
        return FactoryResolver(this).resolve(definition)
    }

    override fun clear() {
        factories.clear()
    }

    private data class RegistryKey(
        val recordClass: KClass<*>,
        val name: String?
    )
}
```

#### 1.2 名前空間管理
```kotlin
object GlobalFactoryRegistry {
    private val registry = DefaultFactoryRegistry()

    fun <T : Record> register(definition: FactoryDefinition<T>) {
        registry.register(definition)
    }

    fun <T : Record> find(recordClass: KClass<T>, name: String? = null): FactoryDefinition<T> {
        return registry.find(recordClass, name)
    }

    fun clear() {
        registry.clear()
    }
}
```

#### 1.3 ファクトリ検索・解決
```kotlin
class FactoryResolver(private val registry: FactoryRegistry) {
    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T> {
        val chain = buildInheritanceChain(definition)
        return mergeChain(chain)
    }

    private fun <T : Record> buildInheritanceChain(
        definition: FactoryDefinition<T>
    ): List<FactoryDefinition<T>> {
        val chain = mutableListOf<FactoryDefinition<T>>()
        var current: FactoryDefinition<T>? = definition

        while (current != null) {
            chain.add(0, current)
            current = current.parent
        }

        return chain
    }

    private fun <T : Record> mergeChain(
        chain: List<FactoryDefinition<T>>
    ): ResolvedFactory<T> {
        val mergedAttributes = mutableMapOf<String, AttributeDefinition<*>>()
        val mergedCallbacks = DefaultCallbackRegistry<T>()
        val mergedTransients = TransientDefinition()

        chain.forEach { def ->
            mergedAttributes.putAll(def.attributes)
            mergedCallbacks.merge(def.callbacks)
            mergedTransients.merge(def.transients)
        }

        return ResolvedFactory(
            definition = chain.last(),
            mergedAttributes = mergedAttributes,
            mergedCallbacks = mergedCallbacks,
            mergedTransients = mergedTransients
        )
    }
}
```

### 2. 基本的なDSL実装

#### 2.1 FactoryDslBuilder
```kotlin
class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>
) {
    private var name: String? = null
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    private val traits = mutableMapOf<String, TraitDefinition<T>>()
    private val callbacks = DefaultCallbackRegistry<T>()
    private val transients = TransientDefinition()

    operator fun <V> String.invoke(value: V) {
        attributes[this] = when (value) {
            is Function0<*> -> DynamicAttribute(value as () -> Any?)
            else -> StaticAttribute(value)
        }
    }

    fun build(): FactoryDefinition<T> {
        return DefaultFactoryDefinition(
            recordClass = recordClass,
            name = name,
            attributes = attributes,
            traits = traits,
            callbacks = callbacks,
            transients = transients
        )
    }
}
```

#### 2.2 DSL関数
```kotlin
inline fun <reified T : Record> factory(
    name: String? = null,
    noinline block: FactoryDslBuilder<T>.() -> Unit
): FactoryDefinition<T> {
    val builder = FactoryDslBuilder(T::class)
    builder.block()
    val definition = builder.build()

    GlobalFactoryRegistry.register(definition)

    return definition
}
```

#### 2.3 属性設定メカニズム
```kotlin
sealed interface AttributeDefinition<T> {
    fun evaluate(context: EvaluationContext): T
}

data class StaticAttribute<T>(val value: T) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext) = value
}

data class DynamicAttribute<T>(
    val generator: (EvaluationContext) -> T
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext) = generator(context)
}
```

### 3. ビルド戦略実装

#### 3.1 build()メソッド
```kotlin
class DefaultFactoryBuilder<T : Record>(
    private val definition: FactoryDefinition<T>,
    private val dsl: DSLContext,
    private val sequenceManager: SequenceManager,
    private val associationResolver: AssociationResolver
) : FactoryBuilder<T> {

    override fun build(overrides: Map<String, Any?>): T {
        val resolved = GlobalFactoryRegistry.resolve(definition)

        val mergedAttributes = resolved.mergedAttributes + overrides.mapValues {
            StaticAttribute(it.value)
        }

        val context = EvaluationContext(
            sequenceManager = sequenceManager,
            associationResolver = associationResolver,
            transients = TransientContext(),
            attributeName = "",
            isCreate = false
        )

        val evaluatedAttributes = mergedAttributes.mapValues { (name, attr) ->
            attr.evaluate(context.withAttributeName(name))
        }

        val record = createRecord(evaluatedAttributes)

        resolved.mergedCallbacks.execute(CallbackPhase.AFTER_BUILD, record)

        return record
    }

    private fun createRecord(attributes: Map<String, Any?>): T {
        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        attributes.forEach { (name, value) ->
            val field = table.field(FieldNameMapper.toSnakeCase(name))
            if (field != null) {
                record.set(field as Field<Any>, value)
            }
        }

        return record
    }
}
```

#### 3.2 create()メソッド
```kotlin
override fun create(overrides: Map<String, Any?>): T {
    val context = EvaluationContext(
        sequenceManager = sequenceManager,
        associationResolver = associationResolver,
        transients = TransientContext(),
        attributeName = "",
        isCreate = true
    )

    val resolved = GlobalFactoryRegistry.resolve(definition)

    val mergedAttributes = resolved.mergedAttributes + overrides.mapValues {
        StaticAttribute(it.value)
    }

    val evaluatedAttributes = mergedAttributes.mapValues { (name, attr) ->
        attr.evaluate(context.withAttributeName(name))
    }

    val record = createRecord(evaluatedAttributes)

    resolved.mergedCallbacks.execute(CallbackPhase.BEFORE_CREATE, record)

    record.store()

    resolved.mergedCallbacks.execute(CallbackPhase.AFTER_CREATE, record)

    return record
}
```

#### 3.3 buildList() / createList()
```kotlin
override fun buildList(count: Int, overrides: Map<String, Any?>): List<T> {
    return (1..count).map { build(overrides) }
}

override fun createList(count: Int, overrides: Map<String, Any?>): List<T> {
    return (1..count).map { create(overrides) }
}
```

#### 3.4 attributes()メソッド
```kotlin
override fun attributes(overrides: Map<String, Any?>): Map<String, Any?> {
    val resolved = GlobalFactoryRegistry.resolve(definition)

    val mergedAttributes = resolved.mergedAttributes + overrides.mapValues {
        StaticAttribute(it.value)
    }

    val context = EvaluationContext(
        sequenceManager = sequenceManager,
        associationResolver = associationResolver,
        transients = TransientContext(),
        attributeName = "",
        isCreate = false
    )

    return mergedAttributes.mapValues { (name, attr) ->
        attr.evaluate(context.withAttributeName(name))
    }
}
```

## テスト実装

### 単体テスト例

```kotlin
class FactoryRegistryTest {
    private lateinit var registry: FactoryRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultFactoryRegistry()
    }

    @Test
    fun `register and find factory`() {
        val definition = DefaultFactoryDefinition(
            recordClass = UserRecord::class,
            name = "user"
        )

        registry.register(definition)

        val found = registry.find(UserRecord::class, "user")

        assertThat(found).isEqualTo(definition)
    }

    @Test
    fun `throw exception when factory not found`() {
        assertThrows<FactoryNotFoundException> {
            registry.find(UserRecord::class, "undefined")
        }
    }
}
```

## チェックリスト

- [ ] FactoryRegistry実装
- [ ] FactoryResolver実装
- [ ] FactoryDslBuilder実装
- [ ] build()実装
- [ ] create()実装
- [ ] buildList()/createList()実装
- [ ] attributes()実装
- [ ] 単体テスト（90%以上カバレッジ）
- [ ] 統合テスト

## 成功基準

- ✅ 基本的なファクトリ定義が動作
- ✅ build()/create()が正常に動作
- ✅ テストカバレッジ90%以上
- ✅ 統合テストが全て成功

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| jOOQ APIの複雑さ | 高 | 事前調査とプロトタイプ |
| パフォーマンス劣化 | 中 | ベンチマークの作成 |

## 次のPhase

[Phase 3: Essentials](./phase3-essentials.md) - 必須機能実装
