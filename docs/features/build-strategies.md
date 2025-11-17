# Build Strategies

## 概要

Build Strategiesは、テストデータの生成方法を選択する機能。用途に応じて4つの戦略を提供。

| 戦略 | DB永続化 | ID生成 | 使用場面 |
|-----|---------|--------|---------|
| `build()` | なし | なし | バリデーションテスト |
| `create()` | あり | あり | 統合テスト |
| `buildStubbed()` | なし | モックID | 単体テスト（v2.0） |
| `attributes()` | なし | なし | APIテスト |

## build()戦略

### 概要
オブジェクトのみを生成し、データベースには保存しない。

### 使用場面
- バリデーションロジックのテスト
- ビジネスロジックの単体テスト
- DB接続不要なテスト

### 使用例

```kotlin
@Test
fun `user validation rejects invalid email`() {
    val user = UserFactory.build(email = "invalid-email")

    val result = UserValidator.validate(user)

    assertThat(result.isValid).isFalse()
    assertThat(result.errors).contains("Invalid email format")
}
```

### 実装

```kotlin
class BuildStrategy<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>
) {
    fun build(overrides: Map<String, Any?> = emptyMap()): T {
        val mergedAttributes = mergeAttributes(definition.attributes, overrides)

        val context = EvaluationContext(
            sequenceManager = SequenceManager.getInstance(),
            associationResolver = AssociationResolver(),
            transients = TransientContext(),
            attributeName = "",
            isCreate = false
        )

        val evaluatedAttributes = evaluateAttributes(mergedAttributes, context)

        val record = createRecord(evaluatedAttributes)

        definition.callbacks.execute(CallbackPhase.AFTER_BUILD, record)

        return record
    }

    private fun createRecord(attributes: Map<String, Any?>): T {
        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        attributes.forEach { (name, value) ->
            val field = table.field(name)
            if (field != null) {
                record.set(field as Field<Any>, value)
            }
        }

        return record
    }
}
```

### 特徴
- **高速**: DB I/Oなし
- **ID未設定**: 主キーはnull
- **トランザクション不要**: ロールバック不要

## create()戦略

### 概要
オブジェクトを生成し、データベースに永続化する。

### 使用場面
- 統合テスト
- E2Eテスト
- 複数テーブル間の関連テスト

### 使用例

```kotlin
@Test
fun `user can create post`() {
    val user = UserFactory.create()
    val post = PostFactory.create(userId = user.id)

    val foundPost = postRepository.findById(post.id)

    assertThat(foundPost).isNotNull()
    assertThat(foundPost.userId).isEqualTo(user.id)
}
```

### 実装

```kotlin
fun create(overrides: Map<String, Any?> = emptyMap()): T {
    val context = EvaluationContext(
        sequenceManager = SequenceManager.getInstance(),
        associationResolver = AssociationResolver(),
        transients = TransientContext(),
        attributeName = "",
        isCreate = true  // createフラグを立てる
    )

    val mergedAttributes = mergeAttributes(definition.attributes, overrides)
    val evaluatedAttributes = evaluateAttributes(mergedAttributes, context)
    val record = createRecord(evaluatedAttributes)

    definition.callbacks.execute(CallbackPhase.BEFORE_CREATE, record)

    record.store()  // jOOQのINSERT

    definition.callbacks.execute(CallbackPhase.AFTER_CREATE, record)

    return record
}
```

### 特徴
- **DB永続化**: INSERTを実行
- **ID自動生成**: DBのAUTO_INCREMENTを使用
- **トランザクション**: コミット/ロールバック可能

## buildList() / createList()

### 概要
複数のレコードを一括生成する。

### 使用例

```kotlin
@Test
fun `pagination works correctly`() {
    val users = UserFactory.createList(100)

    val page1 = userRepository.findAll(PageRequest.of(0, 20))
    val page2 = userRepository.findAll(PageRequest.of(1, 20))

    assertThat(page1.content).hasSize(20)
    assertThat(page2.content).hasSize(20)
    assertThat(page1.content).doesNotContainAnyElementsOf(page2.content)
}
```

### 実装

```kotlin
fun buildList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T> {
    return (1..count).map { build(overrides) }
}

fun createList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T> {
    return (1..count).map { create(overrides) }
}
```

### 最適化版（バッチ挿入）

```kotlin
fun createList(count: Int, overrides: Map<String, Any?> = emptyMap()): List<T> {
    val records = (1..count).map { index ->
        val context = EvaluationContext(
            sequenceManager = SequenceManager.getInstance(),
            associationResolver = AssociationResolver(),
            transients = TransientContext(),
            attributeName = "",
            isCreate = true
        )

        val mergedAttributes = mergeAttributes(definition.attributes, overrides)
        val evaluatedAttributes = evaluateAttributes(mergedAttributes, context)
        createRecord(evaluatedAttributes)
    }

    records.forEach { definition.callbacks.execute(CallbackPhase.BEFORE_CREATE, it) }

    dsl.batchInsert(records).execute()  // バッチINSERT

    records.forEach { definition.callbacks.execute(CallbackPhase.AFTER_CREATE, it) }

    return records
}
```

## attributes()戦略

### 概要
属性をMap形式で取得する。

### 使用場面
- API統合テスト（JSONリクエスト作成）
- データ検証
- デバッグ

### 使用例

```kotlin
@Test
fun `API accepts valid user data`() {
    val userAttrs = UserFactory.attributes(
        name = "Test User",
        email = "test@example.com"
    )

    val response = restTemplate.postForEntity(
        "/api/users",
        userAttrs,
        UserResponse::class.java
    )

    assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
}
```

### 実装

```kotlin
fun attributes(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?> {
    val mergedAttributes = mergeAttributes(definition.attributes, overrides)

    val context = EvaluationContext(
        sequenceManager = SequenceManager.getInstance(),
        associationResolver = AssociationResolver(),
        transients = TransientContext(),
        attributeName = "",
        isCreate = false
    )

    return evaluateAttributes(mergedAttributes, context)
}
```

### 特徴
- **Recordオブジェクトなし**: Map形式で返却
- **アソシエーション**: IDのみ含む
- **高速**: オブジェクト生成のオーバーヘッドなし

## buildStubbed()戦略（v2.0）

### 概要
モック化されたオブジェクトを生成する。

### 使用場面
- 単体テスト（DB不要）
- レポジトリ層のモック
- 高速テスト実行

### 使用例

```kotlin
@Test
fun `service creates audit log on user creation`() {
    val user = UserFactory.buildStubbed(id = 123)

    userService.create(user)

    verify(auditLogRepository).create(
        argThat { it.targetId == 123L }
    )
}
```

### 実装（MockK使用）

```kotlin
fun buildStubbed(overrides: Map<String, Any?> = emptyMap()): T {
    val attributes = attributes(overrides)

    val record = mockk<T>(relaxed = true)

    attributes.forEach { (name, value) ->
        val field = table.field(name)
        if (field != null) {
            every { record.get(field as Field<Any>) } returns value
        }
    }

    if (record.id == null) {
        val mockId = SequenceManager.getInstance().next("stubbed_id") { it.toLong() }
        every { record.id } returns mockId
    }

    definition.callbacks.execute(CallbackPhase.AFTER_BUILD, record)

    return record
}
```

### 特徴
- **DB不要**: 完全にメモリ上で動作
- **モックID**: 一意なIDを自動生成
- **高速**: build()より更に高速

## 戦略の選択ガイドライン

```kotlin
// バリデーションのみテスト → build()
@Test
fun validateEmail() {
    val user = UserFactory.build(email = "invalid")
    assertFalse(user.isValid())
}

// DB永続化が必要 → create()
@Test
fun userCanLogin() {
    val user = UserFactory.create(password = "hashed")
    assertTrue(authService.login(user.email, "raw"))
}

// API統合テスト → attributes()
@Test
fun createUserAPI() {
    val attrs = UserFactory.attributes()
    val response = post("/users", attrs)
    assertEquals(201, response.status)
}

// レポジトリのモック → buildStubbed()
@Test
fun serviceCallsRepository() {
    val user = UserFactory.buildStubbed()
    every { repo.find(user.id) } returns user
    assertEquals(user, service.getUser(user.id))
}
```

## パフォーマンス比較

| 戦略 | 速度 | メモリ | DB接続 |
|-----|------|--------|--------|
| buildStubbed() | ⚡⚡⚡ | 極小 | 不要 |
| build() | ⚡⚡ | 小 | 不要 |
| attributes() | ⚡⚡ | 極小 | 不要 |
| create() | ⚡ | 中 | 必要 |
| createList(1000) | 遅 | 大 | 必要 |
| createBatch(1000) | ⚡ | 中 | 必要 |

## トランザクション管理

### create()とトランザクション

```kotlin
@Test
fun testWithRollback() {
    dsl.transaction { config ->
        val user = UserFactory.create()  // トランザクション内でINSERT

        // テスト実行

        throw Exception("Rollback")  // ロールバック
    }
}
```

**詳細**: [Transactions](./transactions.md)

## エラーハンドリング

### DB制約違反

```kotlin
@Test
fun `create fails on duplicate email`() {
    UserFactory.create(email = "test@example.com")

    assertThrows<DataIntegrityViolationException> {
        UserFactory.create(email = "test@example.com")
    }
}
```

### アソシエーション解決失敗

```kotlin
@Test
fun `create fails on missing association factory`() {
    assertThrows<FactoryNotFoundException> {
        PostFactory.create()  // UserFactoryが未定義
    }
}
```

## ベストプラクティス

### 1. 適切な戦略の選択

```kotlin
class UserServiceTest {
    @Test
    fun `validation logic` () {
        val user = UserFactory.build()  // DB不要
        // バリデーションテスト
    }

    @Test
    fun `database integration`() {
        val user = UserFactory.create()  // DB必要
        // 統合テスト
    }
}
```

### 2. トランザクションスコープの管理

```kotlin
@TestInstance(Lifecycle.PER_METHOD)
class IntegrationTest {
    @BeforeEach
    fun setup() {
        dsl.transaction { /* テスト用トランザクション */ }
    }

    @AfterEach
    fun cleanup() {
        // 自動ロールバック
    }
}
```

### 3. バッチ操作の活用

```kotlin
@Test
fun `large dataset test`() {
    // 遅い
    val users = (1..1000).map { UserFactory.create() }

    // 速い
    val users = UserFactory.createBatch(1000)
}
```

**詳細**: [Batch Operations](./batch-operations.md)

## まとめ

Build Strategiesは以下を実現:
1. **柔軟性**: 用途に応じた戦略選択
2. **パフォーマンス**: build() > create()の速度差
3. **テスタビリティ**: buildStubbed()でDB不要
4. **保守性**: 統一されたインターフェース
