# Factory DSL

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P0
- [Core Interfaces](../architecture/core-interfaces.md) - FactoryDefinition, DSL Builder
- [Build Strategies](./build-strategies.md) - ビルド戦略（依存先）
- [Sequences](./sequences.md) - シーケンス（依存先）
- [Phase 2: Core](../implementation/phase2-core.md) - 実装タスク

## 依存関係
**依存元**: なし（基盤機能）
**依存先**: 全ての機能がこのDSLに依存

## 概要

Factory DSLは、テストデータの定義を宣言的かつ型安全に記述するための中核機能。

### 設計目標
1. **型安全性**: コンパイル時にエラー検出
2. **直感的**: Factory Botライクな記述
3. **拡張性**: カスタム属性定義の容易さ
4. **保守性**: ファクトリ定義の一元管理

## 基本的な使用例

### シンプルなファクトリ

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = "user@example.com"
    age = 25
    createdAt = LocalDateTime.now()
}
```

### 動的な値

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = { Random.nextInt(18, 65) }
    createdAt = { LocalDateTime.now() }
}
```

## DSL構文仕様

### 1. 静的属性

```kotlin
factory<UserRecord> {
    name = "John Doe"
    age = 30
}
```

**コンパイル結果**:
```kotlin
attributes["name"] = StaticAttribute("John Doe")
attributes["age"] = StaticAttribute(30)
```

### 2. 動的属性（ラムダ）

```kotlin
factory<UserRecord> {
    createdAt = { LocalDateTime.now() }
    randomValue = { UUID.randomUUID().toString() }
}
```

**コンパイル結果**:
```kotlin
attributes["createdAt"] = DynamicAttribute { LocalDateTime.now() }
attributes["randomValue"] = DynamicAttribute { UUID.randomUUID().toString() }
```

### 3. シーケンス

```kotlin
factory<UserRecord> {
    email = sequence { n -> "user${n}@example.com" }
    code = sequence("USER") { n -> "USR${n.toString().padStart(5, '0')}" }
}
```

**詳細**: [Sequences](./sequences.md)

### 4. アソシエーション

```kotlin
factory<PostRecord> {
    title = "Default Post"
    user = association<UserRecord>()
}
```

**詳細**: [Associations](./associations.md)

## 高度な使用例

### 名前付きファクトリ

```kotlin
factory<UserRecord>("user") {
    name = "Basic User"
    email = sequence { n -> "user${n}@example.com" }
}

factory<UserRecord>("admin") {
    name = "Admin User"
    email = sequence { n -> "admin${n}@example.com" }
    role = UserRole.ADMIN
}
```

**使用**:
```kotlin
val user = UserFactory.create()           // "user" ファクトリを使用
val admin = UserFactory.create("admin")   // "admin" ファクトリを使用
```

### 属性のオーバーライド

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}

// 使用時にオーバーライド
val user = UserFactory.create(
    name = "Custom User",
    age = 30
)
```

### 複雑な属性計算

```kotlin
factory<UserRecord> {
    firstName = "John"
    lastName = "Doe"
    fullName = { "${get("firstName")} ${get("lastName")}" }
}
```

## DSL実装詳細

### FactoryDslBuilder

```kotlin
class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>
) {
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()

    operator fun String.invoke(value: Any?) {
        attributes[this] = StaticAttribute(value)
    }

    operator fun <V> String.invoke(generator: () -> V) {
        attributes[this] = DynamicAttribute(generator)
    }

    fun <V> sequence(name: String? = null, generator: (Int) -> V): SequenceDefinition<V> {
        return SequenceDefinition(name, generator)
    }

    inline fun <reified A : Record> association(
        factoryName: String? = null,
        overrides: Map<String, Any?> = emptyMap()
    ): AssociationDefinition<A> {
        return AssociationDefinition(A::class, factoryName, overrides)
    }

    fun build(): FactoryDefinition<T> {
        return DefaultFactoryDefinition(
            recordClass = recordClass,
            attributes = attributes
        )
    }
}
```

### プロパティ委譲

```kotlin
class FactoryDslBuilder<T : Record>(private val recordClass: KClass<T>) {
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()

    operator fun <V> setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        attributes[property.name] = when (value) {
            is Function0<*> -> DynamicAttribute(value as () -> Any?)
            is SequenceDefinition<*> -> SequenceAttribute(value.name, value.generator)
            is AssociationDefinition<*> -> AssociationAttribute(
                value.targetClass,
                value.factoryName,
                value.overrides
            )
            else -> StaticAttribute(value)
        }
    }
}

// 使用例
factory<UserRecord> {
    var name by this
    var email by this

    name = "John"
    email = sequence { n -> "user${n}@example.com" }
}
```

### 型推論の活用

```kotlin
inline fun <reified T : Record> factory(
    name: String? = null,
    noinline block: FactoryDslBuilder<T>.() -> Unit
): FactoryDefinition<T> {
    val builder = FactoryDslBuilder(T::class)
    builder.block()
    val definition = builder.build()

    GlobalFactoryRegistry.register(name ?: T::class.simpleName!!, definition)

    return definition
}
```

## エラーハンドリング

### コンパイル時エラー

```kotlin
// OK
factory<UserRecord> {
    name = "John"  // String
}

// NG: コンパイルエラー
factory<UserRecord> {
    name = 123  // Type mismatch
}
```

### 実行時エラー

```kotlin
// 存在しないフィールド
factory<UserRecord> {
    unknownField = "value"  // 実行時エラー
}

// エラーハンドリング
try {
    UserFactory.create()
} catch (e: UnknownFieldException) {
    logger.error("Unknown field: ${e.fieldName}")
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `factory creates user with default values`() {
    factory<UserRecord> {
        name = "Test User"
        email = "test@example.com"
        age = 25
    }

    val user = UserFactory.build()

    assertThat(user.name).isEqualTo("Test User")
    assertThat(user.email).isEqualTo("test@example.com")
    assertThat(user.age).isEqualTo(25)
}
```

### 動的値のテスト

```kotlin
@Test
fun `factory generates unique emails`() {
    factory<UserRecord> {
        name = "Test User"
        email = sequence { n -> "user${n}@example.com" }
    }

    val user1 = UserFactory.build()
    val user2 = UserFactory.build()

    assertThat(user1.email).isNotEqualTo(user2.email)
}
```

## パフォーマンス考慮事項

### 遅延評価

```kotlin
// 属性値は評価時まで計算されない
factory<UserRecord> {
    createdAt = { LocalDateTime.now() }  // build()時に評価
}
```

### ファクトリ定義のキャッシュ

```kotlin
object GlobalFactoryRegistry {
    private val cache = ConcurrentHashMap<String, FactoryDefinition<*>>()

    fun <T : Record> register(name: String, definition: FactoryDefinition<T>) {
        cache[name] = definition
    }

    fun <T : Record> find(name: String): FactoryDefinition<T>? {
        return cache[name] as? FactoryDefinition<T>
    }
}
```

## ベストプラクティス

### 1. ファクトリ定義の配置

```kotlin
// src/test/kotlin/factories/UserFactory.kt
object UserFactories {
    val default = factory<UserRecord> {
        name = "Default User"
        email = sequence { n -> "user${n}@example.com" }
    }

    val admin = factory<UserRecord>("admin") {
        name = "Admin User"
        role = UserRole.ADMIN
    }
}
```

### 2. デフォルト値の設定

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
    isActive = true
    createdAt = { LocalDateTime.now() }
}
```

### 3. 命名規則

- ファクトリ名: 小文字スネークケース（例: `"admin_user"`, `"premium_user"`）
- 属性名: キャメルケース（jOOQの生成コードに合わせる）

## 制限事項

1. **循環参照**: コンパイル時には検出不可、実行時エラー
2. **型安全性の限界**: Mapベースの実装では完全な型安全性は困難
3. **IDEサポート**: カスタムDSLのため補完が限定的

## 今後の拡張

- **KSP（Kotlin Symbol Processing）**: コンパイル時検証の強化
- **IDE Plugin**: 補完・検証サポート
- **マクロ**: Kotlin 2.0のマクロ機能活用

## まとめ

Factory DSLは以下を実現:
1. **宣言的な記述**: 「何を」作るかを明確に
2. **型安全性**: コンパイル時の型チェック
3. **柔軟性**: 静的・動的・シーケンス・アソシエーション
4. **保守性**: ファクトリ定義の一元管理
