# Phase 5: Performance

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Batch Operations](../features/batch-operations.md) - バッチ操作
- [Build Strategies](../features/build-strategies.md) - buildStubbed
- [jOOQ Integration](../architecture/jooq-integration.md) - バッチINSERT
- [Phase 4: Extensions](./phase4-extensions.md) - 前提Phase

## 期間
1週間

## 優先度
P2

## 目標
パフォーマンスを最適化し、大量データ生成を高速化する。

## 前提条件
Phase 4完了

## 成果物
- バッチ挿入最適化
- PreparedStatement再利用
- buildStubbed実装

## タスク一覧

### 1. バッチ操作

#### 1.1 一括INSERT最適化
```kotlin
class BatchBuilder<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>
) {
    fun createBatch(
        count: Int,
        attributesGenerator: (Int) -> Map<String, Any?> = { emptyMap() }
    ): List<T> {
        if (count == 0) return emptyList()

        val records = (1..count).map { index ->
            val attributes = mergeAttributes(
                definition.attributes,
                attributesGenerator(index)
            )

            val evaluatedAttributes = evaluateAttributes(attributes, index)

            buildRecord(evaluatedAttributes)
        }

        // beforeCreateコールバック
        records.forEach { record ->
            definition.callbacks.execute(CallbackPhase.BEFORE_CREATE, record)
        }

        // バッチINSERT
        dsl.batchInsert(records).execute()

        // afterCreateコールバック
        records.forEach { record ->
            definition.callbacks.execute(CallbackPhase.AFTER_CREATE, record)
        }

        return records
    }
}
```

#### 1.2 PreparedStatement再利用
```kotlin
class PreparedStatementCache {
    private val cache = ConcurrentHashMap<String, PreparedStatement>()

    fun getOrCreate(
        sql: String,
        connection: Connection
    ): PreparedStatement {
        return cache.getOrPut(sql) {
            connection.prepareStatement(sql)
        }
    }

    fun clear() {
        cache.values.forEach { it.close() }
        cache.clear()
    }
}
```

#### 1.3 コネクションプーリング
```kotlin
class PooledDSLContextProvider(
    private val dataSource: DataSource
) {
    fun getDSLContext(): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }
}

// HikariCP設定
val config = HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/test"
    username = "user"
    password = "password"
    maximumPoolSize = 10
    minimumIdle = 5
}

val dataSource = HikariDataSource(config)
```

### 2. buildStubbed実装

#### 2.1 MockKとの統合
```kotlin
class StubbedBuilder<T : Record>(
    private val definition: FactoryDefinition<T>,
    private val sequenceManager: SequenceManager
) {
    fun buildStubbed(overrides: Map<String, Any?> = emptyMap()): T {
        val attributes = attributes(overrides)

        val record = mockk<T>(relaxed = true)

        val table = JooqTableResolver.resolveTable(definition.recordClass)

        attributes.forEach { (name, value) ->
            val field = table.field(name)
            if (field != null) {
                every { record.get(field as Field<Any>) } returns value
            }
        }

        // モックIDの生成
        if (record.id == null) {
            val mockId = sequenceManager.next("stubbed_id") { it.toLong() }
            every { record.id } returns mockId
        }

        definition.callbacks.execute(CallbackPhase.AFTER_BUILD, record)

        return record
    }
}
```

#### 2.2 DBアクセスのモック化
```kotlin
class StubbedAssociationResolver : AssociationResolver {
    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        val factory = GlobalFactoryRegistry.find(
            association.targetClass,
            association.factoryName
        )

        val builder = StubbedBuilder(factory, context.sequenceManager)

        return builder.buildStubbed(association.overrides)
    }
}
```

#### 2.3 パフォーマンステスト
```kotlin
@Test
fun `buildStubbed is faster than build`() {
    val buildTime = measureTime {
        (1..1000).forEach { UserFactory.build() }
    }

    val stubbedTime = measureTime {
        (1..1000).forEach { UserFactory.buildStubbed() }
    }

    assertThat(stubbedTime).isLessThan(buildTime / 2)
}
```

## ベンチマーク

### ベンチマーク実装
```kotlin
@State(Scope.Benchmark)
class FactoryBenchmark {

    @Benchmark
    fun individualCreate(blackhole: Blackhole) {
        val user = UserFactory.create()
        blackhole.consume(user)
    }

    @Benchmark
    fun batchCreate(blackhole: Blackhole) {
        val users = UserFactory.createBatch(100)
        blackhole.consume(users)
    }

    @Benchmark
    fun buildStubbed(blackhole: Blackhole) {
        val user = UserFactory.buildStubbed()
        blackhole.consume(user)
    }
}
```

### 目標パフォーマンス
| 操作 | 件数 | 目標時間 |
|-----|------|---------|
| create() | 1 | < 10ms |
| createBatch() | 1000 | < 500ms |
| buildStubbed() | 1000 | < 100ms |

## チェックリスト

- [ ] バッチINSERT実装
- [ ] PreparedStatement再利用実装
- [ ] コネクションプーリング設定
- [ ] buildStubbed実装
- [ ] MockK統合
- [ ] パフォーマンステスト実装
- [ ] ベンチマーク実施
- [ ] 目標パフォーマンス達成

## 成功基準

- ✅ createBatch() が個別create()の20倍以上高速
- ✅ buildStubbed() がbuild()の2倍以上高速
- ✅ 目標パフォーマンス達成
- ✅ メモリ使用量が許容範囲内

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| メモリ不足 | 中 | チャンク分割処理 |
| コネクション枯渇 | 中 | プーリング設定の最適化 |
| buildStubbedの不安定性 | 中 | MockKのバージョン固定 |

## 次のPhase

[Phase 6: Transactions](./phase6-transactions.md) - トランザクション管理
