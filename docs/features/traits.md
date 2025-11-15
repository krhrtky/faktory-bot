# Traits

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P1
- [Factory DSL](./factory-dsl.md) - ファクトリ定義（依存元）
- [Callbacks](./callbacks.md) - コールバック（依存先）
- [Inheritance](./inheritance.md) - ファクトリ継承（関連）
- [Phase 4: Extensions](../implementation/phase4-extensions.md) - 実装タスク

## 依存関係
**依存元**: Factory DSL
**依存先**: Callbacks

## 概要

Traitsは、ファクトリのバリエーションを定義する機能。同じレコード型で異なる状態やロールのテストデータを簡潔に作成できる。

### 設計目標
1. **バリエーション管理**: 1つのファクトリで複数パターン
2. **再利用性**: トレイトの組み合わせ
3. **可読性**: テストの意図が明確
4. **保守性**: 変更の一元管理

## 基本的な使用例

### シンプルなトレイト

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    role = UserRole.MEMBER

    trait("admin") {
        role = UserRole.ADMIN
        permissions = listOf("all")
    }

    trait("premium") {
        subscriptionTier = "premium"
        expiresAt = LocalDateTime.now().plusMonths(1)
    }
}

// 使用
val normalUser = UserFactory.create()
val adminUser = UserFactory.create("admin")
val premiumUser = UserFactory.create("premium")
```

### トレイトの組み合わせ

```kotlin
val premiumAdmin = UserFactory.create("admin", "premium")
// adminとpremiumの両方の属性を持つ
```

## トレイト定義

### 属性のオーバーライド

```kotlin
factory<UserRecord> {
    name = "User"
    age = 25
    isActive = true

    trait("inactive") {
        isActive = false
        deactivatedAt = LocalDateTime.now()
    }

    trait("senior") {
        age = 65
    }
}
```

### コールバック付きトレイト

```kotlin
factory<UserRecord> {
    name = "User"

    trait("with_posts") {
        afterCreate { user ->
            PostFactory.createList(5, userId = user.id)
        }
    }

    trait("with_profile") {
        afterCreate { user ->
            ProfileFactory.create(userId = user.id)
        }
    }
}

val userWithData = UserFactory.create("with_posts", "with_profile")
// user, 5 posts, 1 profile が作成される
```

### 動的な属性

```kotlin
factory<PostRecord> {
    title = "Default Post"
    status = PostStatus.DRAFT

    trait("published") {
        status = PostStatus.PUBLISHED
        publishedAt = { LocalDateTime.now() }
        viewCount = { Random.nextInt(100, 1000) }
    }
}
```

## TraitDefinition実装

### データ構造

```kotlin
data class TraitDefinition<T : Record>(
    val name: String,
    val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    val transients: TransientDefinition = TransientDefinition()
) {
    fun applyTo(definition: FactoryDefinition<T>): FactoryDefinition<T> {
        return definition.copy(
            attributes = definition.attributes + attributes,
            callbacks = definition.callbacks.merge(callbacks),
            transients = definition.transients.merge(transients)
        )
    }
}
```

### トレイト適用ロジック

```kotlin
class TraitApplicator<T : Record> {
    fun apply(
        definition: FactoryDefinition<T>,
        traitNames: List<String>
    ): FactoryDefinition<T> {
        return traitNames.fold(definition) { acc, traitName ->
            val trait = definition.traits[traitName]
                ?: throw TraitNotFoundException(traitName)
            trait.applyTo(acc)
        }
    }
}

class TraitNotFoundException(
    val traitName: String
) : RuntimeException("Trait not found: $traitName")
```

## 高度な使用例

### トレイトの依存関係

```kotlin
factory<UserRecord> {
    name = "User"
    role = UserRole.MEMBER

    trait("admin") {
        role = UserRole.ADMIN
    }

    trait("super_admin") {
        // 暗黙的に "admin" を含む
        role = UserRole.SUPER_ADMIN
        permissions = listOf("all", "manage_admins")
    }
}
```

### 条件付きトレイト

```kotlin
factory<OrderRecord> {
    status = OrderStatus.PENDING
    total = 1000

    trait("paid") {
        status = OrderStatus.PAID
        paidAt = { LocalDateTime.now() }
    }

    trait("shipped") {
        // "paid" が前提
        status = OrderStatus.SHIPPED
        shippedAt = { LocalDateTime.now() }
        trackingNumber = sequence("tracking") { n ->
            "TRK${n.toString().padStart(10, '0')}"
        }
    }
}

// shipped は paid を暗黙的に含む
val order = OrderFactory.create("shipped")
```

### 複雑なビジネスロジック

```kotlin
factory<SubscriptionRecord> {
    plan = "basic"
    price = 1000

    trait("trial") {
        status = SubscriptionStatus.TRIAL
        trialEndsAt = { LocalDateTime.now().plusDays(14) }
        price = 0

        afterCreate { subscription ->
            TrialNotificationFactory.create(subscriptionId = subscription.id)
        }
    }

    trait("expired") {
        status = SubscriptionStatus.EXPIRED
        expiresAt = { LocalDateTime.now().minusDays(1) }

        afterCreate { subscription ->
            ExpirationEmailFactory.create(subscriptionId = subscription.id)
        }
    }
}
```

## トレイトとTransientの組み合わせ

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        val postsCount = 0
        val commentsCount = 0
    }

    trait("active_contributor") {
        transient {
            postsCount = 10
            commentsCount = 50
        }

        afterCreate { user, transients ->
            PostFactory.createList(transients.postsCount, userId = user.id)
            CommentFactory.createList(transients.commentsCount, userId = user.id)
        }
    }
}

val activeUser = UserFactory.create("active_contributor")
// 10 posts, 50 comments が作成される
```

**詳細**: [Transients](./transients.md)

## トレイトの合成

### 優先順位

```kotlin
factory<UserRecord> {
    name = "User"
    age = 25

    trait("young") {
        age = 20
    }

    trait("old") {
        age = 70
    }
}

val user = UserFactory.create("young", "old")
// age = 70 (後から適用されたトレイトが優先)
```

### 明示的な合成

```kotlin
class TraitComposer<T : Record> {
    fun compose(
        traits: List<TraitDefinition<T>>,
        strategy: MergeStrategy = MergeStrategy.LAST_WINS
    ): TraitDefinition<T> {
        return traits.reduce { acc, trait ->
            when (strategy) {
                MergeStrategy.LAST_WINS -> trait.copy(
                    attributes = acc.attributes + trait.attributes
                )
                MergeStrategy.FIRST_WINS -> acc.copy(
                    attributes = trait.attributes + acc.attributes
                )
                MergeStrategy.DEEP_MERGE -> deepMerge(acc, trait)
            }
        }
    }
}

enum class MergeStrategy {
    LAST_WINS,
    FIRST_WINS,
    DEEP_MERGE
}
```

## パフォーマンス考慮事項

### トレイト解決のキャッシュ

```kotlin
class CachedTraitApplicator<T : Record> {
    private val cache = ConcurrentHashMap<Set<String>, FactoryDefinition<T>>()

    fun apply(
        definition: FactoryDefinition<T>,
        traitNames: List<String>
    ): FactoryDefinition<T> {
        val key = traitNames.toSet()

        return cache.getOrPut(key) {
            traitNames.fold(definition) { acc, traitName ->
                val trait = definition.traits[traitName]
                    ?: throw TraitNotFoundException(traitName)
                trait.applyTo(acc)
            }
        }
    }
}
```

### 遅延適用

```kotlin
class LazyTraitDefinition<T : Record>(
    val name: String,
    private val builder: () -> TraitDefinition<T>
) {
    private val cached by lazy { builder() }

    fun get(): TraitDefinition<T> = cached
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `trait overrides default attributes`() {
    factory<UserRecord> {
        role = UserRole.MEMBER

        trait("admin") {
            role = UserRole.ADMIN
        }
    }

    val normalUser = UserFactory.build()
    val adminUser = UserFactory.build("admin")

    assertThat(normalUser.role).isEqualTo(UserRole.MEMBER)
    assertThat(adminUser.role).isEqualTo(UserRole.ADMIN)
}
```

### トレイト組み合わせのテスト

```kotlin
@Test
fun `multiple traits are merged correctly`() {
    factory<UserRecord> {
        name = "User"
        age = 25
        role = UserRole.MEMBER

        trait("admin") {
            role = UserRole.ADMIN
        }

        trait("senior") {
            age = 65
        }
    }

    val user = UserFactory.build("admin", "senior")

    assertThat(user.role).isEqualTo(UserRole.ADMIN)
    assertThat(user.age).isEqualTo(65)
    assertThat(user.name).isEqualTo("User")
}
```

### コールバック付きトレイトのテスト

```kotlin
@Test
fun `trait with callback creates associated records`() {
    factory<UserRecord> {
        name = "User"

        trait("with_posts") {
            afterCreate { user ->
                PostFactory.createList(3, userId = user.id)
            }
        }
    }

    val user = UserFactory.create("with_posts")

    val posts = postRepository.findByUserId(user.id)
    assertThat(posts).hasSize(3)
}
```

## エラーハンドリング

### 未定義トレイトのエラー

```kotlin
@Test
fun `undefined trait throws exception`() {
    factory<UserRecord> {
        name = "User"
    }

    assertThrows<TraitNotFoundException> {
        UserFactory.create("undefined_trait")
    }
}
```

### トレイト競合の検出

```kotlin
class ConflictDetector<T : Record> {
    fun detectConflicts(traits: List<TraitDefinition<T>>): List<String> {
        val conflicts = mutableListOf<String>()
        val attributeTraits = mutableMapOf<String, MutableList<String>>()

        traits.forEach { trait ->
            trait.attributes.keys.forEach { attrName ->
                attributeTraits.getOrPut(attrName) { mutableListOf() }
                    .add(trait.name)
            }
        }

        attributeTraits.forEach { (attrName, traitNames) ->
            if (traitNames.size > 1) {
                conflicts.add("Attribute '$attrName' is defined in: ${traitNames.joinToString()}")
            }
        }

        return conflicts
    }
}
```

## ベストプラクティス

### 1. 意味のある名前

```kotlin
// Good: 意図が明確
trait("admin")
trait("with_posts")
trait("expired")

// Bad: 不明瞭
trait("type1")
trait("variant_a")
```

### 2. 単一責任

```kotlin
// Good: 1つの関心事
trait("admin") {
    role = UserRole.ADMIN
}

trait("with_posts") {
    afterCreate { user ->
        PostFactory.createList(5, userId = user.id)
    }
}

// Bad: 複数の関心事
trait("admin_with_posts") {
    role = UserRole.ADMIN
    afterCreate { user ->
        PostFactory.createList(5, userId = user.id)
    }
}
```

### 3. トレイトの粒度

```kotlin
// Good: 細かい粒度で組み合わせ
trait("verified") { emailVerified = true }
trait("active") { isActive = true }

val user = UserFactory.create("verified", "active")

// Bad: 大きすぎるトレイト
trait("ready_user") {
    emailVerified = true
    isActive = true
    // 他にも多数の属性...
}
```

## 制限事項

1. **順序依存**: トレイトの適用順序が結果に影響
2. **動的トレイト**: 実行時の条件でトレイトを選択不可
3. **トレイト間の依存**: 明示的な依存関係の定義が困難

## 今後の拡張

- **トレイトグループ**: 関連するトレイトをグループ化
- **排他的トレイト**: 同時適用不可のトレイト定義
- **トレイト継承**: トレイト間の継承関係

## まとめ

Traitsは以下を実現:
1. **バリエーション**: 1ファクトリで複数パターン
2. **再利用性**: トレイトの組み合わせ
3. **可読性**: テストの意図が明確
4. **保守性**: 一元管理
