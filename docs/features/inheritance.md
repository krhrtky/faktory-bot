# Inheritance

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P2
- [Factory DSL](./factory-dsl.md) - ファクトリ定義（依存元）
- [Traits](./traits.md) - トレイト（関連）
- [Callbacks](./callbacks.md) - コールバック（依存先）
- [Phase 4: Extensions](../implementation/phase4-extensions.md) - 実装タスク

## 依存関係
**依存元**: Factory DSL
**依存先**: Traits, Callbacks

## 概要

Inheritanceは、ファクトリ定義を継承して新しいファクトリを作成する機能。共通部分を親ファクトリに定義し、DRYを実現。

### 設計目標
1. **DRY原則**: 共通定義の再利用
2. **階層構造**: 段階的な特殊化
3. **オーバーライド**: 柔軟なカスタマイズ
4. **保守性**: 変更の一元管理

## 基本的な使用例

### シンプルな継承

```kotlin
// ベースファクトリ
factory<UserRecord>("user") {
    name = "Basic User"
    email = sequence { n -> "user${n}@example.com" }
    role = UserRole.MEMBER
    isActive = true
}

// 継承したファクトリ
factory<UserRecord>("admin", parent = "user") {
    role = UserRole.ADMIN
    permissions = listOf("all")
}

factory<UserRecord>("premium_user", parent = "user") {
    subscriptionTier = "premium"
    expiresAt = LocalDateTime.now().plusMonths(1)
}

// 使用
val basicUser = UserFactory.create("user")
val admin = UserFactory.create("admin")
val premium = UserFactory.create("premium_user")
```

### 多段階継承

```kotlin
factory<UserRecord>("user") {
    name = "User"
    role = UserRole.MEMBER
}

factory<UserRecord>("staff", parent = "user") {
    role = UserRole.STAFF
    department = "General"
}

factory<UserRecord>("manager", parent = "staff") {
    role = UserRole.MANAGER
    managedTeamSize = 5
}

factory<UserRecord>("director", parent = "manager") {
    role = UserRole.DIRECTOR
    managedTeamSize = 20
    budgetAuthority = 10_000_000
}
```

## 継承メカニズム

### 属性のマージ

```kotlin
factory<UserRecord>("base") {
    name = "User"
    age = 25
    email = sequence { n -> "user${n}@example.com" }
}

factory<UserRecord>("special", parent = "base") {
    age = 30  // オーバーライド
    role = UserRole.ADMIN  // 追加
}

// 結果:
// name = "User" (継承)
// age = 30 (オーバーライド)
// email = sequence (継承)
// role = UserRole.ADMIN (追加)
```

### FactoryResolver実装

```kotlin
interface FactoryResolver {
    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T>
}

class DefaultFactoryResolver(
    private val registry: FactoryRegistry
) : FactoryResolver {

    override fun <T : Record> resolve(
        definition: FactoryDefinition<T>
    ): ResolvedFactory<T> {
        val chain = buildInheritanceChain(definition)
        return mergeChain(chain)
    }

    private fun <T : Record> buildInheritanceChain(
        definition: FactoryDefinition<T>
    ): List<FactoryDefinition<T>> {
        val chain = mutableListOf<FactoryDefinition<T>>()
        var current: FactoryDefinition<T>? = definition

        while (current != null) {
            chain.add(0, current)  // 先頭に追加（親が先）
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

        chain.forEach { definition ->
            mergedAttributes.putAll(definition.attributes)
            mergedCallbacks.merge(definition.callbacks)
            mergedTransients.merge(definition.transients)
        }

        return ResolvedFactory(
            definition = chain.last(),
            mergedAttributes = mergedAttributes,
            mergedCallbacks = mergedCallbacks,
            mergedTransients = mergedTransients
        )
    }
}

data class ResolvedFactory<T : Record>(
    val definition: FactoryDefinition<T>,
    val mergedAttributes: Map<String, AttributeDefinition<*>>,
    val mergedCallbacks: CallbackRegistry<T>,
    val mergedTransients: TransientDefinition
)
```

## 高度な使用例

### コールバックの継承

```kotlin
factory<UserRecord>("base") {
    name = "User"

    afterCreate { user ->
        println("Base: User created")
    }
}

factory<UserRecord>("with_profile", parent = "base") {
    afterCreate { user ->
        println("with_profile: Creating profile")
        ProfileFactory.create(userId = user.id)
    }
}

factory<UserRecord>("with_posts", parent = "with_profile") {
    afterCreate { user ->
        println("with_posts: Creating posts")
        PostFactory.createList(5, userId = user.id)
    }
}

UserFactory.create("with_posts")
// 出力:
// Base: User created
// with_profile: Creating profile
// with_posts: Creating posts
```

### Transientの継承

```kotlin
factory<UserRecord>("base") {
    name = "User"

    transient {
        val skipValidation = false
        val sendEmail = false
    }

    beforeCreate { user, transients ->
        if (!transients.skipValidation) {
            ValidationService.validate(user)
        }
    }
}

factory<UserRecord>("test_user", parent = "base") {
    transient {
        skipValidation = true  // オーバーライド
        createSampleData = true  // 追加
    }

    afterCreate { user, transients ->
        if (transients.createSampleData) {
            PostFactory.createList(10, userId = user.id)
        }
    }
}
```

### トレイトとの組み合わせ

```kotlin
factory<UserRecord>("user") {
    name = "User"

    trait("admin") {
        role = UserRole.ADMIN
    }
}

factory<UserRecord>("premium_user", parent = "user") {
    subscriptionTier = "premium"

    trait("trial") {
        subscriptionTier = "trial"
        trialEndsAt = LocalDateTime.now().plusDays(14)
    }
}

// 使用
val premiumAdmin = UserFactory.create("premium_user", "admin")
// premium + admin

val trialAdmin = UserFactory.create("premium_user", "trial", "admin")
// trial + admin
```

**詳細**: [Traits](./traits.md)

## 継承チェーンの可視化

### デバッグ用ヘルパー

```kotlin
fun <T : Record> printInheritanceChain(definition: FactoryDefinition<T>) {
    val chain = buildInheritanceChain(definition)

    println("Inheritance Chain:")
    chain.forEachIndexed { index, factory ->
        println("  ${"  ".repeat(index)}├─ ${factory.name ?: "anonymous"}")
        println("  ${"  ".repeat(index)}│  Attributes: ${factory.attributes.keys}")
        println("  ${"  ".repeat(index)}│  Callbacks: ${factory.callbacks.count()}")
    }
}

// 使用
printInheritanceChain(UserFactory.getDefinition("director"))
// 出力:
// Inheritance Chain:
//   ├─ user
//   │  Attributes: [name, role]
//   │  Callbacks: 0
//     ├─ staff
//     │  Attributes: [department]
//     │  Callbacks: 1
//       ├─ manager
//       │  Attributes: [managedTeamSize]
//       │  Callbacks: 1
//         ├─ director
//         │  Attributes: [budgetAuthority]
//         │  Callbacks: 2
```

## エラーハンドリング

### 循環継承の検出

```kotlin
class CircularInheritanceDetector {
    fun <T : Record> detect(definition: FactoryDefinition<T>) {
        val visited = mutableSetOf<String>()
        var current: FactoryDefinition<T>? = definition

        while (current != null) {
            val name = current.name ?: throw IllegalStateException("Anonymous factory in chain")

            if (name in visited) {
                throw CircularInheritanceException(visited.toList() + name)
            }

            visited.add(name)
            current = current.parent
        }
    }
}

class CircularInheritanceException(
    val chain: List<String>
) : RuntimeException(
    "Circular inheritance detected: ${chain.joinToString(" -> ")}"
)
```

### 未定義親ファクトリのエラー

```kotlin
factory<UserRecord>("child", parent = "undefined_parent") {
    name = "Child"
}

// エラー
try {
    UserFactory.create("child")
} catch (e: ParentFactoryNotFoundException) {
    println("Parent factory 'undefined_parent' not found")
}

class ParentFactoryNotFoundException(
    val parentName: String
) : RuntimeException("Parent factory not found: $parentName")
```

## パフォーマンス考慮事項

### 解決結果のキャッシュ

```kotlin
class CachingFactoryResolver(
    private val delegate: FactoryResolver
) : FactoryResolver {

    private val cache = ConcurrentHashMap<String, ResolvedFactory<*>>()

    override fun <T : Record> resolve(
        definition: FactoryDefinition<T>
    ): ResolvedFactory<T> {
        val key = definition.name ?: return delegate.resolve(definition)

        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) {
            delegate.resolve(definition)
        } as ResolvedFactory<T>
    }

    fun clearCache() {
        cache.clear()
    }
}
```

### 遅延解決

```kotlin
class LazyFactoryDefinition<T : Record>(
    override val recordClass: KClass<T>,
    override val name: String?,
    private val parentName: String?,
    private val registry: FactoryRegistry
) : FactoryDefinition<T> {

    override val parent: FactoryDefinition<T>? by lazy {
        parentName?.let {
            registry.find(recordClass, it)
        }
    }

    // その他のプロパティ...
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `child factory inherits parent attributes`() {
    factory<UserRecord>("parent") {
        name = "Parent User"
        age = 30
    }

    factory<UserRecord>("child", parent = "parent") {
        role = UserRole.ADMIN
    }

    val user = UserFactory.build("child")

    assertThat(user.name).isEqualTo("Parent User")
    assertThat(user.age).isEqualTo(30)
    assertThat(user.role).isEqualTo(UserRole.ADMIN)
}
```

### オーバーライドのテスト

```kotlin
@Test
fun `child factory overrides parent attributes`() {
    factory<UserRecord>("parent") {
        name = "Parent User"
        age = 30
    }

    factory<UserRecord>("child", parent = "parent") {
        name = "Child User"  // オーバーライド
    }

    val user = UserFactory.build("child")

    assertThat(user.name).isEqualTo("Child User")
    assertThat(user.age).isEqualTo(30)
}
```

### コールバック継承のテスト

```kotlin
@Test
fun `child factory inherits parent callbacks`() {
    val executionOrder = mutableListOf<String>()

    factory<UserRecord>("parent") {
        name = "User"

        afterCreate { user ->
            executionOrder.add("parent")
        }
    }

    factory<UserRecord>("child", parent = "parent") {
        afterCreate { user ->
            executionOrder.add("child")
        }
    }

    UserFactory.create("child")

    assertThat(executionOrder).containsExactly("parent", "child")
}
```

### 循環継承のテスト

```kotlin
@Test
fun `circular inheritance is detected`() {
    // この定義はコンパイル時には作れないので、
    // リフレクションやカスタムビルダーでテスト

    assertThrows<CircularInheritanceException> {
        // A -> B -> C -> A のような循環
    }
}
```

## ベストプラクティス

### 1. 継承階層は浅く

```kotlin
// Good: 2-3段階
user -> staff -> manager

// Bad: 深すぎる階層
user -> staff -> manager -> senior_manager -> director -> vp -> ceo
```

### 2. 意味のある階層

```kotlin
// Good: 概念的に意味がある
factory<UserRecord>("user") { /* 基本 */ }
factory<UserRecord>("staff", parent = "user") { /* スタッフ */ }
factory<UserRecord>("manager", parent = "staff") { /* 管理職 */ }

// Bad: 無意味な階層
factory<UserRecord>("user_v1") { }
factory<UserRecord>("user_v2", parent = "user_v1") { }
factory<UserRecord>("user_v3", parent = "user_v2") { }
```

### 3. トレイトとの使い分け

```kotlin
// Good: 継承は「is-a」関係
factory<UserRecord>("user") { }
factory<UserRecord>("admin", parent = "user") { /* AdminはUserの一種 */ }

// Good: トレイトは「has-a」関係
factory<UserRecord> {
    trait("with_posts") { /* Userが投稿を持つ */ }
}

// Bad: 継承を状態の違いに使う
factory<UserRecord>("active_user", parent = "user") { isActive = true }
factory<UserRecord>("inactive_user", parent = "user") { isActive = false }
// これはトレイトで表現すべき
```

## 制限事項

1. **単一継承**: 複数の親ファクトリは不可
2. **型制約**: 親子は同じRecord型のみ
3. **動的継承**: 実行時に親を変更不可

## 今後の拡張

- **Mix-in**: トレイト的な多重継承
- **抽象ファクトリ**: インスタンス化不可のベースファクトリ
- **ファクトリ合成**: 複数のファクトリを合成

## まとめ

Inheritanceは以下を実現:
1. **DRY原則**: 共通定義の再利用
2. **階層構造**: 段階的な特殊化
3. **オーバーライド**: 柔軟なカスタマイズ
4. **保守性**: 一元管理
