# Associations

## 概要

Associationsは、関連レコードを自動的に生成・解決する機能。外部キー関係のあるテストデータを簡潔に作成できる。

### 設計目標
1. **自動解決**: 関連レコードを自動生成
2. **循環参照検出**: 無限ループの防止
3. **カスタマイズ可能**: 関連レコードの属性変更
4. **遅延評価**: 必要時のみ生成

## 基本的な使用例

### シンプルなアソシエーション

```kotlin
factory<PostRecord> {
    title = "Default Post"
    content = "Content"
    user = association<UserRecord>()  // UserRecordを自動生成
}

// 使用
val post = PostFactory.create()
// post.user.id が自動的に設定される
```

### 属性のカスタマイズ

```kotlin
factory<PostRecord> {
    title = "Default Post"
    author = association<UserRecord> {
        name = "Custom Author"
        email = "author@example.com"
    }
}

// 生成されるUserRecordは指定した属性を持つ
```

### 名前付きファクトリの使用

```kotlin
factory<PostRecord> {
    title = "Default Post"
    author = association<UserRecord>("admin") {
        name = "Admin Author"
    }
}

// "admin" ファクトリを使用してUserRecordを生成
```

## アソシエーション戦略

### 1. 新規生成

```kotlin
factory<CommentRecord> {
    content = "Comment"
    post = association<PostRecord>()  // 新しいPostRecordを生成
}

val comment = CommentFactory.create()
// comment.post は新規作成されたレコード
```

### 2. 既存レコードの指定

```kotlin
val existingUser = UserFactory.create()

val post = PostFactory.create(
    userId = existingUser.id
)

// 既存のUserを参照
```

### 3. 条件付き生成

```kotlin
factory<PostRecord> {
    title = "Post"
    user = association<UserRecord>().takeIf { shouldCreateUser }
        ?: existingUser
}
```

## build() vs create() での動作

### build()の場合

```kotlin
factory<PostRecord> {
    title = "Post"
    user = association<UserRecord>()
}

val post = PostFactory.build()
// user も build() される（DB永続化なし）
```

### create()の場合

```kotlin
val post = PostFactory.create()
// user も create() される（DB永続化あり）
// post.userId に user.id が自動設定
```

## AssociationResolver実装

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

### 実装

```kotlin
class DefaultAssociationResolver(
    private val factoryRegistry: FactoryRegistry
) : AssociationResolver {

    private val circularDetector = CircularDependencyDetector()

    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        return circularDetector.withCheck(association.targetClass) {
            val factory = factoryRegistry.find(
                association.targetClass,
                association.factoryName
            )

            val builder = FactoryBuilder(
                factory,
                context.dsl,
                context.sequenceManager,
                this
            )

            val newContext = context.withDepth(context.depth + 1)

            if (context.isCreate) {
                builder.create(association.overrides)
            } else {
                builder.build(association.overrides)
            }
        }
    }

    override fun checkCircular(recordClass: KClass<*>) {
        circularDetector.check(recordClass)
    }
}
```

## 循環参照の検出

### CircularDependencyDetector

```kotlin
class CircularDependencyDetector {
    private val stack = ThreadLocal.withInitial { mutableListOf<KClass<*>>() }

    fun <T> withCheck(recordClass: KClass<*>, block: () -> T): T {
        val currentStack = stack.get()

        if (recordClass in currentStack) {
            throw CircularAssociationException(
                chain = currentStack + recordClass
            )
        }

        currentStack.add(recordClass)
        try {
            return block()
        } finally {
            currentStack.removeLast()
        }
    }

    fun check(recordClass: KClass<*>) {
        val currentStack = stack.get()
        if (recordClass in currentStack) {
            throw CircularAssociationException(currentStack + recordClass)
        }
    }
}

class CircularAssociationException(
    val chain: List<KClass<*>>
) : RuntimeException(
    "Circular association detected: ${chain.joinToString(" -> ") { it.simpleName ?: "Unknown" }}"
)
```

### 循環参照の例

```kotlin
// NG: 循環参照
factory<UserRecord> {
    name = "User"
    defaultPost = association<PostRecord>()
}

factory<PostRecord> {
    title = "Post"
    user = association<UserRecord>()
}

// UserFactory.create() → PostFactory.create() → UserFactory.create() → ...
// CircularAssociationException がスローされる
```

### 回避策

```kotlin
// OK: 一方向の関連のみ
factory<UserRecord> {
    name = "User"
}

factory<PostRecord> {
    title = "Post"
    user = association<UserRecord>()
}

// または、コールバックで後から設定
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        PostFactory.create(userId = user.id)
    }
}
```

## 外部キーの自動設定

### jOOQのForeignKey情報活用

```kotlin
class ForeignKeyAssociationResolver(
    private val factoryRegistry: FactoryRegistry
) : AssociationResolver {

    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        val associatedRecord = createAssociatedRecord(association, context)

        // 外部キーフィールドを自動設定
        setForeignKey(context.currentRecord, associatedRecord)

        return associatedRecord
    }

    private fun <T : Record> setForeignKey(
        record: Record,
        associatedRecord: T
    ) {
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

## 高度な使用例

### 複数の関連

```kotlin
factory<CommentRecord> {
    content = "Comment"
    post = association<PostRecord>()
    user = association<UserRecord>()
}

val comment = CommentFactory.create()
// comment.post と comment.user の両方が自動生成される
```

### ネストした関連

```kotlin
factory<CommentRecord> {
    content = "Comment"
    post = association<PostRecord> {
        title = "Post with User"
        user = association<UserRecord> {
            name = "Nested User"
        }
    }
}

// Comment → Post → User の3階層が自動生成
```

### 同じレコード型の複数アソシエーション

```kotlin
factory<TransferRecord> {
    amount = 1000
    fromAccount = association<AccountRecord>("personal")
    toAccount = association<AccountRecord>("business")
}

// 2つの異なるAccountRecordが生成される
```

### リストのアソシエーション

```kotlin
factory<UserRecord> {
    name = "User"

    afterCreate { user ->
        PostFactory.createList(3, userId = user.id)
    }
}

val user = UserFactory.create()
// user.id に紐づく3つのPostが作成される
```

## パフォーマンス最適化

### 遅延評価

```kotlin
class LazyAssociationAttribute<T : Record>(
    val targetClass: KClass<T>,
    val factoryName: String? = null,
    val overrides: () -> Map<String, Any?>
) : AttributeDefinition<T> {

    private var cached: T? = null

    override fun evaluate(context: EvaluationContext): T {
        if (cached != null && !context.isCreate) {
            return cached!!
        }

        val result = context.associationResolver.resolve(this, context)
        if (!context.isCreate) {
            cached = result
        }

        return result
    }
}
```

### バッチ生成の最適化

```kotlin
// 非効率: N+1問題
val posts = (1..100).map {
    PostFactory.create()  // 各Postに対してUserを生成
}

// 効率的: Userを先に生成
val user = UserFactory.create()
val posts = PostFactory.createList(100, userId = user.id)
```

## エラーハンドリング

### ファクトリ未定義エラー

```kotlin
class FactoryNotFoundException(
    recordClass: KClass<*>,
    factoryName: String?
) : RuntimeException(
    "Factory not found for ${recordClass.simpleName}" +
    (factoryName?.let { " (name: $it)" } ?: "")
)

// 使用
try {
    PostFactory.create()
} catch (e: FactoryNotFoundException) {
    logger.error("UserFactory is not defined")
}
```

### 深さ制限

```kotlin
class DepthLimitedAssociationResolver(
    private val delegate: AssociationResolver,
    private val maxDepth: Int = 10
) : AssociationResolver {

    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        if (context.depth >= maxDepth) {
            throw AssociationDepthExceededException(maxDepth)
        }

        return delegate.resolve(association, context)
    }
}

class AssociationDepthExceededException(
    val maxDepth: Int
) : RuntimeException("Association depth exceeded: $maxDepth")
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `association creates related record`() {
    factory<PostRecord> {
        title = "Post"
        user = association<UserRecord>()
    }

    val post = PostFactory.create()

    assertThat(post.userId).isNotNull()
    val user = userRepository.findById(post.userId)
    assertThat(user).isNotNull()
}
```

### カスタマイズのテスト

```kotlin
@Test
fun `association respects overrides`() {
    factory<PostRecord> {
        title = "Post"
        user = association<UserRecord> {
            name = "Custom Name"
        }
    }

    val post = PostFactory.create()
    val user = userRepository.findById(post.userId)

    assertThat(user.name).isEqualTo("Custom Name")
}
```

### 循環参照のテスト

```kotlin
@Test
fun `circular association throws exception`() {
    factory<UserRecord> {
        name = "User"
        defaultPost = association<PostRecord>()
    }

    factory<PostRecord> {
        title = "Post"
        user = association<UserRecord>()
    }

    assertThrows<CircularAssociationException> {
        UserFactory.create()
    }
}
```

## ベストプラクティス

### 1. 明示的な外部キー設定

```kotlin
// Good: 明示的
val user = UserFactory.create()
val post = PostFactory.create(userId = user.id)

// Bad: アソシエーションの過度な使用（パフォーマンス低下）
val post = PostFactory.create {
    user = association<UserRecord>()
}
```

### 2. 循環参照の回避

```kotlin
// Good: 一方向の関連
factory<PostRecord> {
    user = association<UserRecord>()
}

// Bad: 双方向の関連
factory<UserRecord> {
    posts = association<PostRecord>()  // NG
}
```

### 3. 必要な関連のみ定義

```kotlin
// Good: 必須の関連のみ
factory<PostRecord> {
    user = association<UserRecord>()
}

// Bad: 任意の関連も自動生成
factory<PostRecord> {
    category = association<CategoryRecord>()  // null可能なら不要
}
```

## まとめ

Associationsは以下を実現:
1. **自動化**: 関連レコードの自動生成
2. **安全性**: 循環参照の検出
3. **柔軟性**: カスタマイズ可能
4. **効率性**: 遅延評価とバッチ最適化
