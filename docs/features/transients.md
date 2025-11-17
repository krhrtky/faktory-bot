# Transients

## 概要

Transientsは、レコードには永続化されないテスト用のパラメータを定義する機能。コールバックや動的属性の制御に使用。

### 設計目標
1. **非永続化**: DBに保存されない一時的な値
2. **コールバック連携**: afterCreate等でパラメータ受け渡し
3. **テスト制御**: テストシナリオの柔軟な制御
4. **型安全性**: 可能な限り型安全に

## 基本的な使用例

### シンプルなTransient

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        val postsCount = 5
    }

    afterCreate { user, transients ->
        repeat(transients.postsCount) {
            PostFactory.create(userId = user.id)
        }
    }
}

// デフォルト値（5件）で作成
val user1 = UserFactory.create()

// オーバーライド（10件）で作成
val user2 = UserFactory.create(postsCount = 10)
```

### バリデーションスキップ

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    transient {
        val skipValidation = false
    }

    beforeCreate { user, transients ->
        if (!transients.skipValidation) {
            UserValidator.validate(user)
        }
    }
}

// バリデーションスキップ
val user = UserFactory.create(skipValidation = true)
```

### 条件付き関連作成

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        val withPosts = true
        val withComments = false
    }

    afterCreate { user, transients ->
        if (transients.withPosts) {
            PostFactory.createList(5, userId = user.id)
        }

        if (transients.withComments) {
            CommentFactory.createList(10, userId = user.id)
        }
    }
}

val userWithPosts = UserFactory.create(withPosts = true)
val userWithBoth = UserFactory.create(withPosts = true, withComments = true)
```

## TransientDefinition実装

### データ構造

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
```

### 実行時コンテキスト

```kotlin
data class TransientContext(
    private val values: Map<String, Any?> = emptyMap()
) {
    inline operator fun <reified T> get(key: String): T {
        return values[key] as? T
            ?: throw TransientNotFoundException(key)
    }

    inline fun <reified T> getOrNull(key: String): T? {
        return values[key] as? T
    }

    inline fun <reified T> getOrDefault(key: String, default: T): T {
        return values[key] as? T ?: default
    }

    fun with(key: String, value: Any?) = TransientContext(
        values + (key to value)
    )

    fun contains(key: String) = key in values
}

class TransientNotFoundException(
    val key: String
) : RuntimeException("Transient property not found: $key")
```

## 高度な使用例

### 複雑なテストシナリオ

```kotlin
factory<OrderRecord> {
    total = 1000
    status = OrderStatus.PENDING

    transient {
        val includeShipping = true
        val includeGiftWrap = false
        val applyDiscount = false
        val discountRate = 0.1
    }

    afterBuild { order, transients ->
        var calculatedTotal = order.total

        if (transients.includeShipping) {
            calculatedTotal += 500
        }

        if (transients.includeGiftWrap) {
            calculatedTotal += 200
        }

        if (transients.applyDiscount) {
            calculatedTotal *= (1 - transients.discountRate)
        }

        order.total = calculatedTotal.toInt()
    }
}

val order = OrderFactory.build(
    includeShipping = true,
    includeGiftWrap = true,
    applyDiscount = true,
    discountRate = 0.2
)
// total = (1000 + 500 + 200) * 0.8 = 1360
```

### 動的な関連数

```kotlin
factory<ProjectRecord> {
    name = "Project"

    transient {
        val membersCount = 3
        val tasksCount = 10
        val milestonesCount = 2
    }

    afterCreate { project, transients ->
        UserFactory.createList(transients.membersCount).forEach { user ->
            ProjectMemberFactory.create(
                projectId = project.id,
                userId = user.id
            )
        }

        TaskFactory.createList(transients.tasksCount, projectId = project.id)

        MilestoneFactory.createList(transients.milestonesCount, projectId = project.id)
    }
}

val smallProject = ProjectFactory.create(
    membersCount = 2,
    tasksCount = 5,
    milestonesCount = 1
)

val largeProject = ProjectFactory.create(
    membersCount = 10,
    tasksCount = 50,
    milestonesCount = 5
)
```

### ファイル生成制御

```kotlin
factory<DocumentRecord> {
    title = "Document"

    transient {
        val generatePdf = false
        val generateThumbnail = false
    }

    afterCreate { document, transients ->
        if (transients.generatePdf) {
            PdfGenerator.generate(document)
        }

        if (transients.generateThumbnail) {
            ThumbnailGenerator.generate(document)
        }
    }
}

val documentWithPdf = DocumentFactory.create(generatePdf = true)
```

## トレイトとの組み合わせ

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        val postsCount = 0
    }

    trait("blogger") {
        transient {
            postsCount = 20
        }

        afterCreate { user, transients ->
            PostFactory.createList(transients.postsCount, userId = user.id)
        }
    }

    trait("power_blogger") {
        transient {
            postsCount = 100
        }

        afterCreate { user, transients ->
            PostFactory.createList(transients.postsCount, userId = user.id)
            BlogBadgeFactory.create(userId = user.id, type = "power_blogger")
        }
    }
}

val blogger = UserFactory.create("blogger")
// 20 posts

val powerBlogger = UserFactory.create("power_blogger")
// 100 posts + badge

val customBlogger = UserFactory.create("blogger", postsCount = 50)
// 50 posts
```

**詳細**: [Traits](./traits.md)

## Transientの評価

### 評価タイミング

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

### 動的な評価

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        val createdAt = { LocalDateTime.now() }
        val randomSeed = { Random.nextInt() }
    }

    afterCreate { user, transients ->
        val timestamp = transients.createdAt()
        val seed = transients.randomSeed()

        AuditLogFactory.create(
            userId = user.id,
            timestamp = timestamp,
            seed = seed
        )
    }
}
```

## 型安全なTransient

### 型付きTransient定義

```kotlin
class TypedTransientBuilder<T : Record> {
    private val properties = mutableMapOf<String, TypedTransientProperty<*>>()

    fun <V> property(name: String, default: V): TypedTransientProperty<V> {
        val property = TypedTransientProperty(name, default)
        properties[name] = property
        return property
    }

    fun build(): TransientDefinition {
        return TransientDefinition(
            properties = properties.mapValues { it.value.defaultValue }
        )
    }
}

data class TypedTransientProperty<T>(
    val name: String,
    val defaultValue: T
)

// 使用例
factory<UserRecord> {
    name = "User"

    val postsCount = transientProperty("postsCount", 5)
    val withProfile = transientProperty("withProfile", false)

    afterCreate { user, transients ->
        repeat(transients[postsCount]) {
            PostFactory.create(userId = user.id)
        }

        if (transients[withProfile]) {
            ProfileFactory.create(userId = user.id)
        }
    }
}
```

### 列挙型Transient

```kotlin
enum class UserSetupMode {
    MINIMAL,
    STANDARD,
    COMPLETE
}

factory<UserRecord> {
    name = "User"

    transient {
        val setupMode = UserSetupMode.STANDARD
    }

    afterCreate { user, transients ->
        when (transients.setupMode) {
            UserSetupMode.MINIMAL -> {
                // 最小限のセットアップ
            }
            UserSetupMode.STANDARD -> {
                ProfileFactory.create(userId = user.id)
                PostFactory.createList(5, userId = user.id)
            }
            UserSetupMode.COMPLETE -> {
                ProfileFactory.create(userId = user.id)
                PostFactory.createList(20, userId = user.id)
                SettingsFactory.create(userId = user.id)
                NotificationFactory.createList(10, userId = user.id)
            }
        }
    }
}

val user = UserFactory.create(setupMode = UserSetupMode.COMPLETE)
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `transient controls callback behavior`() {
    factory<UserRecord> {
        name = "User"

        transient {
            val postsCount = 3
        }

        afterCreate { user, transients ->
            PostFactory.createList(transients.postsCount, userId = user.id)
        }
    }

    val user = UserFactory.create()

    val posts = postRepository.findByUserId(user.id)
    assertThat(posts).hasSize(3)
}
```

### オーバーライドのテスト

```kotlin
@Test
fun `transient can be overridden`() {
    factory<UserRecord> {
        name = "User"

        transient {
            val postsCount = 3
        }

        afterCreate { user, transients ->
            PostFactory.createList(transients.postsCount, userId = user.id)
        }
    }

    val user = UserFactory.create(postsCount = 10)

    val posts = postRepository.findByUserId(user.id)
    assertThat(posts).hasSize(10)
}
```

### 複雑なシナリオのテスト

```kotlin
@Test
fun `transients enable complex test scenarios`() {
    factory<OrderRecord> {
        total = 1000

        transient {
            val includeShipping = false
            val applyDiscount = false
        }

        afterBuild { order, transients ->
            var total = order.total

            if (transients.includeShipping) total += 500
            if (transients.applyDiscount) total = (total * 0.9).toInt()

            order.total = total
        }
    }

    val order1 = OrderFactory.build()
    val order2 = OrderFactory.build(includeShipping = true)
    val order3 = OrderFactory.build(includeShipping = true, applyDiscount = true)

    assertThat(order1.total).isEqualTo(1000)
    assertThat(order2.total).isEqualTo(1500)
    assertThat(order3.total).isEqualTo(1350)
}
```

## エラーハンドリング

### 未定義Transientのエラー

```kotlin
@Test
fun `undefined transient throws exception`() {
    factory<UserRecord> {
        name = "User"

        afterCreate { user, transients ->
            val undefined = transients.get<Int>("undefined")  // 例外
        }
    }

    assertThrows<TransientNotFoundException> {
        UserFactory.create()
    }
}
```

### 型不一致のエラー

```kotlin
@Test
fun `type mismatch in transient throws exception`() {
    factory<UserRecord> {
        name = "User"

        transient {
            val count = 5  // Int
        }

        afterCreate { user, transients ->
            val count = transients.get<String>("count")  // ClassCastException
        }
    }

    assertThrows<ClassCastException> {
        UserFactory.create()
    }
}
```

## ベストプラクティス

### 1. デフォルト値の設定

```kotlin
// Good: デフォルト値あり
transient {
    val postsCount = 5
}

// Bad: デフォルト値なし（nullableになる）
transient {
    val postsCount: Int? = null
}
```

### 2. 意味のある名前

```kotlin
// Good: 意図が明確
transient {
    val skipEmailNotification = false
    val generatePdfReport = true
}

// Bad: 不明瞭
transient {
    val flag1 = false
    val option2 = true
}
```

### 3. 型安全性の活用

```kotlin
// Good: 列挙型で型安全
enum class ReportFormat { PDF, EXCEL, CSV }

transient {
    val format = ReportFormat.PDF
}

// Bad: 文字列（タイポのリスク）
transient {
    val format = "pdf"
}
```

## まとめ

Transientsは以下を実現:
1. **非永続化**: テスト用パラメータの受け渡し
2. **柔軟性**: 動的なテストシナリオ
3. **コールバック連携**: afterCreate等での活用
4. **テスト制御**: 複雑なセットアップの簡潔化
