# Core Interfaces

## インターフェース設計方針

1. **DSL指向**: jOOQ TableField を使った型安全で自然な記述
2. **実行時検証**: NOT NULL制約の実行時チェックと明確なエラーメッセージ
3. **jOOQ連携**: メタデータから型情報を自動抽出
4. **拡張性**: sealed interface で拡張ポイントを明確化
5. **イミュータビリティ**: データクラスは不変

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
@InternalFactoryApi
data class DefaultFactoryDefinition<T : Record>(
    override val recordClass: KClass<T>,
    override val name: String? = null,
    override val parent: FactoryDefinition<T>? = null,
    override val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    override val traits: Map<String, TraitDefinition<T>> = emptyMap(),
    override val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    override val transients: TransientDefinition = TransientDefinition()
) : FactoryDefinition<T>
```

## 2. AttributeDefinition

### 責務
属性値の評価戦略を定義する

### Sealed Interface

```kotlin
sealed interface AttributeDefinition<T> {
    fun evaluate(context: EvaluationContext): T
}

data class StaticAttribute<T>(val value: T) : AttributeDefinition<T>
data class DynamicAttribute<T>(val generator: (EvaluationContext) -> T) : AttributeDefinition<T>
data class SequenceAttribute<T>(val name: String?, val generator: (Int) -> T) : AttributeDefinition<T>
data class AssociationAttribute<T : Record>(
    val targetClass: KClass<T>,
    val factoryName: String?,
    val overrides: Map<String, Any?>
) : AttributeDefinition<T>
```

## 3. Factory DSL

### Type-Safe DSL with jOOQ TableField

```kotlin
factory<UsersRecord> {
    USERS.NAME set "User"
    USERS.EMAIL set sequence { n -> "user$n@example.com" }
    USERS.AGE set 25

    trait("admin") {
        USERS.NAME set "Admin User"
    }

    afterCreate { user, _ ->
        println("Created user: ${user.id}")
    }
}
```

### DSL Builder Implementation

```kotlin
class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>
) {
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    private val traits = mutableMapOf<String, TraitDefinition<T>>()
    private val callbacks = DefaultCallbackRegistry<T>()

    // Type-safe TableField extension
    infix fun <V> TableField<T, V>.set(value: V) {
        attributes[name] = StaticAttribute(value)
    }

    infix fun <V> TableField<T, V>.set(generator: (Int) -> V) {
        attributes[name] = SequenceAttribute(name, generator)
    }

    fun trait(name: String, block: FactoryDslBuilder<T>.() -> Unit) {
        val traitBuilder = FactoryDslBuilder(recordClass)
        traitBuilder.block()
        traits[name] = TraitDefinition(name, traitBuilder.build())
    }

    fun afterBuild(callback: (T, EvaluationContext) -> Unit) {
        callbacks.addAfterBuild(callback)
    }

    fun afterCreate(callback: (T, EvaluationContext) -> Unit) {
        callbacks.addAfterCreate(callback)
    }

    fun build(): FactoryDefinition<T> {
        return DefaultFactoryDefinition(
            recordClass = recordClass,
            attributes = attributes,
            traits = traits,
            callbacks = callbacks
        )
    }
}
```

## 4. FactoryBuilder

### Build Strategies

```kotlin
interface FactoryBuilder<T : Record> {
    fun build(overrides: Map<String, Any?> = emptyMap()): T
    fun create(overrides: Map<String, Any?> = emptyMap()): T
    fun buildList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T>
    fun createList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T>
    fun attributes(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?>
}
```

## 5. Required Field Validation

### Runtime Validation

```kotlin
class RequiredAttributeValidator {
    fun <T : Record> validateRequiredAttributes(
        record: T,
        table: Table<T>
    ) {
        val missingFields = table.fields()
            .filter { field ->
                !field.dataType.nullable() &&
                field.getValue(record) == null &&
                !hasDefaultValue(field)
            }
            .map { it.name }

        if (missingFields.isNotEmpty()) {
            throw MissingRequiredAttributesException(
                "Missing required attributes for table '${table.name}': ${missingFields.joinToString()}"
            )
        }
    }
}
```

## 6. CallbackRegistry

### Lifecycle Hooks

```kotlin
interface CallbackRegistry<T : Record> {
    fun addAfterBuild(callback: (T, EvaluationContext) -> Unit)
    fun addBeforeCreate(callback: (T, EvaluationContext) -> Unit)
    fun addAfterCreate(callback: (T, EvaluationContext) -> Unit)

    fun executeAfterBuild(record: T, context: EvaluationContext)
    fun executeBeforeCreate(record: T, context: EvaluationContext)
    fun executeAfterCreate(record: T, context: EvaluationContext)
}
```

## まとめ

これらのインターフェースにより以下を実現:

1. **DSL指向**: jOOQ TableField による型安全なDSL
2. **実行時検証**: NOT NULL制約の明確なエラーメッセージ
3. **拡張性**: sealed interface による拡張ポイント
4. **テスタビリティ**: インターフェース駆動設計
5. **保守性**: 単一責任の原則
6. **柔軟性**: trait、callback、sequence による強力な機能

### アーキテクチャ階層

```
┌──────────────────────────────────────┐
│  Factory DSL Layer (User-facing)     │
│  - factory<T> { }                    │
│  - USERS.NAME set "value"            │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│  Factory Registry & Builder          │
│  - GlobalFactoryRegistry             │
│  - DefaultFactoryBuilder             │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│  Core Interfaces                     │
│  - FactoryDefinition                 │
│  - AttributeDefinition               │
│  - CallbackRegistry                  │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│  jOOQ Integration                    │
│  - JooqTableResolver                 │
│  - RequiredAttributeValidator        │
│  - DSLContext operations             │
└──────────────────────────────────────┘
```

次のステップ: 各インターフェースの詳細な実装とテストケース作成
