# Callbacks

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P1
- [Factory DSL](./factory-dsl.md) - ファクトリ定義（依存元）
- [Build Strategies](./build-strategies.md) - ビルド戦略（依存元）
- [Transients](./transients.md) - Transient属性（関連）
- [Phase 4: Extensions](../implementation/phase4-extensions.md) - 実装タスク

## 依存関係
**依存元**: Factory DSL, Build Strategies
**依存先**: なし（独立機能）

## 概要

Callbacksは、レコードのライフサイクルにフックを提供する機能。オブジェクト生成前後に任意の処理を実行できる。

### 設計目標
1. **ライフサイクル管理**: build/create の各段階でフック
2. **柔軟性**: 任意の処理を挿入可能
3. **チェーン可能**: 複数のコールバックを登録
4. **テスト支援**: 関連データの自動生成等

## コールバックの種類

| フック | タイミング | 使用場面 |
|-------|----------|---------|
| `afterBuild` | build()後 | ログ出力、検証 |
| `beforeCreate` | INSERT前 | タイムスタンプ設定、前処理 |
| `afterCreate` | INSERT後 | 関連データ生成、通知 |

## 基本的な使用例

### afterBuild

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    afterBuild { user ->
        logger.debug("Built user: ${user.name}")
    }
}

val user = UserFactory.build()
// ログ: "Built user: User"
```

### beforeCreate

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    beforeCreate { user ->
        user.createdAt = LocalDateTime.now()
        user.updatedAt = LocalDateTime.now()
    }
}

val user = UserFactory.create()
// createdAt, updatedAt が自動設定される
```

### afterCreate

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        ProfileFactory.create(userId = user.id)
        SettingsFactory.create(userId = user.id)
    }
}

val user = UserFactory.create()
// user と一緒に profile, settings が作成される
```

## CallbackRegistry実装

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

enum class CallbackPhase {
    AFTER_BUILD,
    BEFORE_CREATE,
    AFTER_CREATE
}
```

### 実装

```kotlin
class DefaultCallbackRegistry<T : Record> : CallbackRegistry<T> {
    private val afterBuildCallbacks = mutableListOf<(T, TransientContext) -> Unit>()
    private val beforeCreateCallbacks = mutableListOf<(T, TransientContext) -> Unit>()
    private val afterCreateCallbacks = mutableListOf<(T, TransientContext) -> Unit>()

    override fun afterBuild(callback: (T) -> Unit) {
        afterBuildCallbacks.add { record, _ -> callback(record) }
    }

    override fun afterBuild(callback: (T, TransientContext) -> Unit) {
        afterBuildCallbacks.add(callback)
    }

    override fun beforeCreate(callback: (T) -> Unit) {
        beforeCreateCallbacks.add { record, _ -> callback(record) }
    }

    override fun beforeCreate(callback: (T, TransientContext) -> Unit) {
        beforeCreateCallbacks.add(callback)
    }

    override fun afterCreate(callback: (T) -> Unit) {
        afterCreateCallbacks.add { record, _ -> callback(record) }
    }

    override fun afterCreate(callback: (T, TransientContext) -> Unit) {
        afterCreateCallbacks.add(callback)
    }

    override fun execute(
        phase: CallbackPhase,
        record: T,
        transients: TransientContext
    ) {
        val callbacks = when (phase) {
            CallbackPhase.AFTER_BUILD -> afterBuildCallbacks
            CallbackPhase.BEFORE_CREATE -> beforeCreateCallbacks
            CallbackPhase.AFTER_CREATE -> afterCreateCallbacks
        }

        callbacks.forEach { it(record, transients) }
    }

    override fun merge(other: CallbackRegistry<T>): CallbackRegistry<T> {
        val merged = DefaultCallbackRegistry<T>()
        merged.afterBuildCallbacks.addAll(this.afterBuildCallbacks)
        merged.beforeCreateCallbacks.addAll(this.beforeCreateCallbacks)
        merged.afterCreateCallbacks.addAll(this.afterCreateCallbacks)

        if (other is DefaultCallbackRegistry) {
            merged.afterBuildCallbacks.addAll(other.afterBuildCallbacks)
            merged.beforeCreateCallbacks.addAll(other.beforeCreateCallbacks)
            merged.afterCreateCallbacks.addAll(other.afterCreateCallbacks)
        }

        return merged
    }
}
```

## 高度な使用例

### Transientとの組み合わせ

```kotlin
factory<UserRecord> {
    name = "User"

    transient {
        val sendWelcomeEmail = true
        val createSampleData = false
    }

    afterCreate { user, transients ->
        if (transients.sendWelcomeEmail) {
            EmailService.sendWelcomeEmail(user.email)
        }

        if (transients.createSampleData) {
            PostFactory.createList(5, userId = user.id)
            CommentFactory.createList(10, userId = user.id)
        }
    }
}

val user = UserFactory.create(
    sendWelcomeEmail = false,
    createSampleData = true
)
```

**詳細**: [Transients](./transients.md)

### 複数のコールバック

```kotlin
factory<UserRecord> {
    name = "User"

    afterBuild { user ->
        logger.debug("User built: ${user.name}")
    }

    afterBuild { user ->
        ValidationService.validate(user)
    }

    beforeCreate { user ->
        user.createdAt = LocalDateTime.now()
    }

    beforeCreate { user ->
        user.uuid = UUID.randomUUID().toString()
    }

    afterCreate { user ->
        AuditLogFactory.create(
            action = "USER_CREATED",
            targetId = user.id
        )
    }

    afterCreate { user ->
        NotificationFactory.create(
            userId = user.id,
            type = "WELCOME"
        )
    }
}
```

### 条件付きコールバック

```kotlin
factory<OrderRecord> {
    total = 1000
    status = OrderStatus.PENDING

    afterCreate { order ->
        if (order.total >= 10000) {
            NotificationFactory.create(
                type = "LARGE_ORDER",
                targetId = order.id
            )
        }
    }

    afterCreate { order ->
        if (order.status == OrderStatus.PAID) {
            ShippingFactory.create(orderId = order.id)
        }
    }
}
```

### 外部サービス連携

```kotlin
factory<UserRecord> {
    name = "User"
    email = sequence { n -> "user${n}@example.com" }

    afterCreate { user ->
        // 外部API呼び出し
        try {
            ExternalUserService.registerUser(user.id, user.email)
        } catch (e: Exception) {
            logger.warn("Failed to register user to external service", e)
        }
    }
}
```

### 関連データの一括生成

```kotlin
factory<ProjectRecord> {
    name = "Project"

    afterCreate { project ->
        // チームメンバー作成
        val owner = UserFactory.create()
        ProjectMemberFactory.create(
            projectId = project.id,
            userId = owner.id,
            role = MemberRole.OWNER
        )

        val members = UserFactory.createList(5)
        members.forEach { member ->
            ProjectMemberFactory.create(
                projectId = project.id,
                userId = member.id,
                role = MemberRole.MEMBER
            )
        }

        // 初期タスク作成
        TaskFactory.createList(10, projectId = project.id)

        // 初期マイルストーン作成
        MilestoneFactory.create(
            projectId = project.id,
            name = "Phase 1",
            dueDate = LocalDate.now().plusMonths(3)
        )
    }
}
```

## コールバックチェーン

### 実行順序

```kotlin
factory<UserRecord> {
    name = "User"

    afterBuild { user ->
        println("1. afterBuild")
    }

    beforeCreate { user ->
        println("2. beforeCreate")
    }

    afterCreate { user ->
        println("3. afterCreate")
    }
}

UserFactory.create()
// 出力:
// 1. afterBuild
// 2. beforeCreate
// 3. afterCreate
```

### 親ファクトリとのマージ

```kotlin
factory<UserRecord>("base") {
    name = "User"

    afterCreate { user ->
        println("Base: afterCreate")
    }
}

factory<UserRecord>("admin", parent = "base") {
    role = UserRole.ADMIN

    afterCreate { user ->
        println("Admin: afterCreate")
    }
}

UserFactory.create("admin")
// 出力:
// Base: afterCreate
// Admin: afterCreate
```

**詳細**: [Inheritance](./inheritance.md)

## エラーハンドリング

### コールバック内のエラー

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        try {
            ExternalService.notify(user.id)
        } catch (e: Exception) {
            logger.error("Callback failed", e)
            // エラーを再スローするか、無視するか選択
            // throw CallbackException("Failed to notify external service", e)
        }
    }
}
```

### トランザクションとの連携

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        // afterCreateはトランザクション内で実行される
        // エラーが発生するとロールバックされる
        ExternalService.createAccount(user.id)
        // 失敗した場合、userの作成もロールバック
    }
}
```

**詳細**: [Transactions](./transactions.md)

### カスタム例外

```kotlin
class CallbackException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        if (!ValidationService.isValid(user)) {
            throw CallbackException("Invalid user created: ${user.id}")
        }
    }
}
```

## パフォーマンス考慮事項

### 重い処理の回避

```kotlin
// Bad: afterCreateで重い処理
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        // 大量のデータ生成（遅い）
        PostFactory.createList(1000, userId = user.id)
    }
}

// Good: Transientで制御
factory<UserRecord> {
    name = "User"

    transient {
        val withPosts = false
        val postsCount = 0
    }

    afterCreate { user, transients ->
        if (transients.withPosts) {
            PostFactory.createList(transients.postsCount, userId = user.id)
        }
    }
}

val user = UserFactory.create()  // 高速（関連データなし）
val userWithPosts = UserFactory.create(withPosts = true, postsCount = 10)
```

### 非同期処理

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        // 非同期で実行（テストの完了を待たない）
        CompletableFuture.runAsync {
            ExternalService.notify(user.id)
        }
    }
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `callback is executed`() {
    var callbackExecuted = false

    factory<UserRecord> {
        name = "User"

        afterCreate { user ->
            callbackExecuted = true
        }
    }

    UserFactory.create()

    assertThat(callbackExecuted).isTrue()
}
```

### 複数コールバックのテスト

```kotlin
@Test
fun `multiple callbacks are executed in order`() {
    val executionOrder = mutableListOf<String>()

    factory<UserRecord> {
        name = "User"

        afterBuild { user ->
            executionOrder.add("afterBuild")
        }

        beforeCreate { user ->
            executionOrder.add("beforeCreate")
        }

        afterCreate { user ->
            executionOrder.add("afterCreate")
        }
    }

    UserFactory.create()

    assertThat(executionOrder).containsExactly(
        "afterBuild",
        "beforeCreate",
        "afterCreate"
    )
}
```

### Transient連携のテスト

```kotlin
@Test
fun `callback receives transients`() {
    factory<UserRecord> {
        name = "User"

        transient {
            val postsCount = 5
        }

        afterCreate { user, transients ->
            PostFactory.createList(transients.postsCount, userId = user.id)
        }
    }

    val user = UserFactory.create(postsCount = 3)

    val posts = postRepository.findByUserId(user.id)
    assertThat(posts).hasSize(3)
}
```

## ベストプラクティス

### 1. コールバックの責務を明確に

```kotlin
// Good: 明確な責務
afterCreate { user ->
    AuditLogFactory.create(action = "USER_CREATED", targetId = user.id)
}

afterCreate { user ->
    NotificationFactory.create(userId = user.id, type = "WELCOME")
}

// Bad: 複数の責務
afterCreate { user ->
    AuditLogFactory.create(action = "USER_CREATED", targetId = user.id)
    NotificationFactory.create(userId = user.id, type = "WELCOME")
    EmailService.send(user.email)
    ExternalService.notify(user.id)
}
```

### 2. 副作用を最小限に

```kotlin
// Good: テストデータのみ
afterCreate { user ->
    ProfileFactory.create(userId = user.id)
}

// Bad: 外部サービス呼び出し（テストが遅くなる）
afterCreate { user ->
    ExternalEmailService.send(user.email)  // 実際のメール送信
}
```

### 3. Transientで制御

```kotlin
// Good: デフォルトでは実行しない
transient {
    val sendEmail = false
}

afterCreate { user, transients ->
    if (transients.sendEmail) {
        EmailService.send(user.email)
    }
}

// 必要な時だけ有効化
val user = UserFactory.create(sendEmail = true)
```

## まとめ

Callbacksは以下を実現:
1. **ライフサイクル管理**: build/create各段階でフック
2. **柔軟性**: 任意の処理を挿入
3. **自動化**: 関連データの自動生成
4. **拡張性**: 複数のコールバックをチェーン
