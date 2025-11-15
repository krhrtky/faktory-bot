# Core Interfaces

## 関連ドキュメント
- [System Design](./system-design.md) - アーキテクチャ全体像
- [jOOQ Integration](./jooq-integration.md) - jOOQ固有の実装
- [Phase 2: Core](../implementation/phase2-core.md) - コア機能実装タスク

## インターフェース設計方針

1. **型安全性**: Kotlin のジェネリクスを最大限活用
2. **DSL指向**: 自然な記述を可能にするAPI設計
3. **拡張性**: sealed interface で拡張ポイントを明確化
4. **イミュータビリティ**: データクラスは不変

## 1. FactoryDefinition

### 責務
ファクトリの定義を保持する不変オブジェクト

### 依存
- `AttributeDefinition`
- `TraitDefinition`
- `CallbackRegistry`

### インターフェース

```kotlin
interface FactoryDefinition<T : Record> {
    val recordClass: KClass<T>
    val name: String?
    val parent: FactoryDefinition<T>?
    val attributes: Map<String, AttributeDefinition<*>>
    val traits: Map<String, TraitDefinition<T>>
    val callbacks: CallbackRegistry<T>
    val transients: TransientDefinition

    fun withParent(parent: FactoryDefinition<T>): FactoryDefinition<T>
    fun withAttribute(name: String, definition: AttributeDefinition<*>): FactoryDefinition<T>
    fun withTrait(name: String, trait: TraitDefinition<T>): FactoryDefinition<T>
}
```

### 実装例

```kotlin
data class DefaultFactoryDefinition<T : Record>(
    override val recordClass: KClass<T>,
    override val name: String? = null,
    override val parent: FactoryDefinition<T>? = null,
    override val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    override val traits: Map<String, TraitDefinition<T>> = emptyMap(),
    override val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    override val transients: TransientDefinition = TransientDefinition()
) : FactoryDefinition<T> {
    override fun withParent(parent: FactoryDefinition<T>) = copy(parent = parent)
    override fun withAttribute(name: String, definition: AttributeDefinition<*>) =
        copy(attributes = attributes + (name to definition))
    override fun withTrait(name: String, trait: TraitDefinition<T>) =
        copy(traits = traits + (name to trait))
}
```

## 2. AttributeDefinition

### 責務
属性の定義（静的値、動的値、シーケンス、アソシエーション）

### sealed interface構造

```kotlin
sealed interface AttributeDefinition<T> {
    fun evaluate(context: EvaluationContext): T
}

data class StaticAttribute<T>(
    val value: T
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext) = value
}

data class DynamicAttribute<T>(
    val generator: (EvaluationContext) -> T
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext) = generator(context)
}

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

## 3. EvaluationContext

### 責務
属性評価時のコンテキスト情報を提供

### インターフェース

```kotlin
data class EvaluationContext(
    val sequenceManager: SequenceManager,
    val associationResolver: AssociationResolver,
    val transients: TransientContext,
    val attributeName: String,
    val depth: Int = 0
) {
    fun withDepth(newDepth: Int) = copy(depth = newDepth)
    fun withAttributeName(name: String) = copy(attributeName = name)
}
```

## 4. FactoryRegistry

### 責務
ファクトリの登録・検索・解決

### インターフェース

```kotlin
interface FactoryRegistry {
    fun <T : Record> register(definition: FactoryDefinition<T>)
    fun <T : Record> register(name: String, definition: FactoryDefinition<T>)

    fun <T : Record> find(recordClass: KClass<T>): FactoryDefinition<T>
    fun <T : Record> find(recordClass: KClass<T>, name: String): FactoryDefinition<T>

    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T>

    fun clear()
}
```

### ResolvedFactory

```kotlin
data class ResolvedFactory<T : Record>(
    val definition: FactoryDefinition<T>,
    val mergedAttributes: Map<String, AttributeDefinition<*>>,
    val mergedCallbacks: CallbackRegistry<T>,
    val mergedTransients: TransientDefinition
)
```

## 5. FactoryBuilder

### 責務
レコードの生成・永続化

### インターフェース

```kotlin
interface FactoryBuilder<T : Record> {
    fun build(overrides: Map<String, Any?> = emptyMap()): T
    fun build(vararg traits: String, overrides: Map<String, Any?> = emptyMap()): T

    fun create(overrides: Map<String, Any?> = emptyMap()): T
    fun create(vararg traits: String, overrides: Map<String, Any?> = emptyMap()): T

    fun buildList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T>
    fun createList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T>

    fun attributes(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?>
}
```

### BuildStrategy

```kotlin
interface BuildStrategy {
    fun <T : Record> build(
        recordClass: KClass<T>,
        attributes: Map<String, Any?>
    ): T

    fun <T : Record> create(
        recordClass: KClass<T>,
        attributes: Map<String, Any?>,
        dsl: DSLContext
    ): T
}
```

## 6. SequenceManager

### 責務
シーケンス値の管理

### インターフェース

```kotlin
interface SequenceManager {
    fun <T> next(name: String, generator: (Int) -> T): T
    fun current(name: String): Int
    fun reset(name: String)
    fun resetAll()
}
```

### 実装例

```kotlin
class DefaultSequenceManager : SequenceManager {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>()

    override fun <T> next(name: String, generator: (Int) -> T): T {
        val counter = sequences.computeIfAbsent(name) { AtomicInteger(0) }
        val value = counter.incrementAndGet()
        return generator(value)
    }

    override fun current(name: String): Int =
        sequences[name]?.get() ?: 0

    override fun reset(name: String) {
        sequences[name]?.set(0)
    }

    override fun resetAll() {
        sequences.clear()
    }
}
```

## 7. AssociationResolver

### 責務
関連レコードの解決

### インターフェース

```kotlin
interface AssociationResolver {
    fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T

    fun checkCircular(recordClass: KClass<*>)
}
```

### CircularDependencyDetector

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

## 8. CallbackRegistry

### 責務
コールバックの登録・実行

### インターフェース

```kotlin
interface CallbackRegistry<T : Record> {
    fun afterBuild(callback: (T) -> Unit)
    fun afterBuild(callback: (T, TransientContext) -> Unit)

    fun beforeCreate(callback: (T) -> Unit)
    fun beforeCreate(callback: (T, TransientContext) -> Unit)

    fun afterCreate(callback: (T) -> Unit)
    fun afterCreate(callback: (T, TransientContext) -> Unit)

    fun execute(phase: CallbackPhase, record: T, transients: TransientContext = TransientContext())

    fun merge(other: CallbackRegistry<T>): CallbackRegistry<T>
}
```

### CallbackPhase

```kotlin
enum class CallbackPhase {
    AFTER_BUILD,
    BEFORE_CREATE,
    AFTER_CREATE
}
```

## 9. TraitDefinition

### 責務
トレイトの定義

### インターフェース

```kotlin
data class TraitDefinition<T : Record>(
    val name: String,
    val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry()
) {
    fun applyTo(definition: FactoryDefinition<T>): FactoryDefinition<T> {
        return definition.copy(
            attributes = definition.attributes + attributes,
            callbacks = definition.callbacks.merge(callbacks)
        )
    }
}
```

## 10. TransientDefinition

### 責務
Transient属性の定義

### インターフェース

```kotlin
data class TransientDefinition(
    val properties: Map<String, Any?> = emptyMap()
) {
    fun <T> get(key: String): T? = properties[key] as? T
    fun with(key: String, value: Any?) = copy(properties = properties + (key to value))
}

data class TransientContext(
    private val values: Map<String, Any?> = emptyMap()
) {
    operator fun <T> get(key: String): T? = values[key] as? T
    fun with(key: String, value: Any?) = TransientContext(values + (key to value))
}
```

## DSL Builder

### FactoryDslBuilder

```kotlin
class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>
) {
    private var name: String? = null
    private var parent: FactoryDefinition<T>? = null
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    private val traits = mutableMapOf<String, TraitDefinition<T>>()
    private val callbacks = DefaultCallbackRegistry<T>()
    private val transients = TransientDefinition()

    fun attribute(name: String, value: Any?) {
        attributes[name] = StaticAttribute(value)
    }

    fun <V> attribute(name: String, generator: () -> V) {
        attributes[name] = DynamicAttribute { generator() }
    }

    fun <V> sequence(name: String? = null, generator: (Int) -> V): V {
        // プレースホルダー: 実際の評価は後で行う
        throw UnsupportedOperationException("Use in factory definition")
    }

    fun <A : Record> association(
        targetClass: KClass<A>,
        factoryName: String? = null,
        overrides: Map<String, Any?> = emptyMap()
    ): A {
        // プレースホルダー
        throw UnsupportedOperationException("Use in factory definition")
    }

    fun trait(name: String, block: FactoryDslBuilder<T>.() -> Unit) {
        val traitBuilder = FactoryDslBuilder(recordClass)
        traitBuilder.block()
        traits[name] = TraitDefinition(
            name = name,
            attributes = traitBuilder.attributes,
            callbacks = traitBuilder.callbacks
        )
    }

    fun afterBuild(callback: (T) -> Unit) {
        callbacks.afterBuild(callback)
    }

    fun beforeCreate(callback: (T) -> Unit) {
        callbacks.beforeCreate(callback)
    }

    fun afterCreate(callback: (T) -> Unit) {
        callbacks.afterCreate(callback)
    }

    fun build(): FactoryDefinition<T> {
        return DefaultFactoryDefinition(
            recordClass = recordClass,
            name = name,
            parent = parent,
            attributes = attributes,
            traits = traits,
            callbacks = callbacks,
            transients = transients
        )
    }
}
```

### DSL関数

```kotlin
inline fun <reified T : Record> factory(
    name: String? = null,
    noinline block: FactoryDslBuilder<T>.() -> Unit
): FactoryDefinition<T> {
    val builder = FactoryDslBuilder(T::class)
    builder.block()
    return builder.build()
}
```

## 型安全性の保証

### コンパイル時チェック

```kotlin
// OK: 型安全
factory<UserRecord> {
    name = "John"  // String
    age = 30       // Int
}

// NG: コンパイルエラー
factory<UserRecord> {
    name = 123     // Type mismatch
}
```

### 実行時バリデーション

```kotlin
interface AttributeValidator<T> {
    fun validate(value: T): ValidationResult
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}
```

## まとめ

これらのインターフェースにより以下を実現:

1. **型安全性**: コンパイル時の型チェック
2. **拡張性**: sealed interface による拡張ポイント
3. **テスタビリティ**: インターフェース駆動設計
4. **保守性**: 単一責任の原則
5. **パフォーマンス**: 遅延評価とイミュータビリティ
