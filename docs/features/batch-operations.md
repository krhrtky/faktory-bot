# Batch Operations

## 関連ドキュメント
- [Product Backlog](../planning/product-backlog.md) - 優先度: P2
- [Build Strategies](./build-strategies.md) - createList戦略（依存元）
- [jOOQ Integration](../architecture/jooq-integration.md) - バッチINSERT
- [Phase 5: Performance](../implementation/phase5-performance.md) - 実装タスク

## 依存関係
**依存元**: Build Strategies
**依存先**: jOOQ Integration

## 概要

Batch Operationsは、大量のテストデータを効率的に生成する機能。個別INSERT の代わりにバッチINSERTを使用してパフォーマンスを向上。

### 設計目標
1. **高速化**: バッチINSERTで劇的に高速化
2. **メモリ効率**: ストリーミング処理対応
3. **準備statement再利用**: 同じSQLの再利用
4. **使いやすさ**: 既存APIとの互換性

## 基本的な使用例

### createList() の最適化

```kotlin
// 従来（遅い）: 1,000回のINSERT
val users = (1..1000).map { UserFactory.create() }

// 最適化（速い）: 1回のバッチINSERT
val users = UserFactory.createList(1000)
```

### createBatch()

```kotlin
// より明示的なバッチ操作
val users = UserFactory.createBatch(1000) { index ->
    name = "User $index"
    email = "user${index}@example.com"
}
```

## パフォーマンス比較

### ベンチマーク

```kotlin
@Test
fun performanceBenchmark() {
    // 個別INSERT: ~10秒
    measureTime {
        (1..1000).forEach {
            UserFactory.create()
        }
    }.also { println("Individual: $it") }

    // バッチINSERT: ~0.5秒
    measureTime {
        UserFactory.createBatch(1000)
    }.also { println("Batch: $it") }
}

// 結果:
// Individual: 10.234s
// Batch: 0.512s
// → 約20倍高速
```

### データ量別の性能

| 件数 | 個別INSERT | バッチINSERT | 高速化率 |
|-----|-----------|-------------|---------|
| 10 | 0.1s | 0.05s | 2x |
| 100 | 1.0s | 0.1s | 10x |
| 1,000 | 10.0s | 0.5s | 20x |
| 10,000 | 100.0s | 3.0s | 33x |

## 実装

### BatchBuilder

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

        val table = JooqTableResolver.resolveTable(definition.recordClass)

        // レコード準備
        val records = (1..count).map { index ->
            val attributes = mergeAttributes(
                definition.attributes,
                attributesGenerator(index)
            )

            val evaluatedAttributes = evaluateAttributes(attributes, index)

            buildRecord(table, evaluatedAttributes)
        }

        // beforeCreateコールバック実行
        records.forEach { record ->
            definition.callbacks.execute(CallbackPhase.BEFORE_CREATE, record)
        }

        // バッチINSERT
        dsl.batchInsert(records).execute()

        // afterCreateコールバック実行
        records.forEach { record ->
            definition.callbacks.execute(CallbackPhase.AFTER_CREATE, record)
        }

        return records
    }

    private fun evaluateAttributes(
        attributes: Map<String, AttributeDefinition<*>>,
        index: Int
    ): Map<String, Any?> {
        val context = EvaluationContext(
            sequenceManager = SequenceManager.getInstance(),
            associationResolver = AssociationResolver(),
            transients = TransientContext(),
            attributeName = "",
            isCreate = true,
            batchIndex = index
        )

        return attributes.mapValues { (name, attr) ->
            attr.evaluate(context.withAttributeName(name))
        }
    }

    private fun buildRecord(
        table: Table<T>,
        attributes: Map<String, Any?>
    ): T {
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

### jOOQ BatchInsert活用

```kotlin
fun <T : Record> DSLContext.batchInsertRecords(
    records: List<T>
): IntArray {
    if (records.isEmpty()) return intArrayOf()

    // jOOQのbatchInsert APIを使用
    return this.batchInsert(records).execute()
}
```

## 高度な使用例

### チャンク分割

```kotlin
class ChunkedBatchBuilder<T : Record>(
    private val delegate: BatchBuilder<T>,
    private val chunkSize: Int = 1000
) {
    fun createBatch(
        count: Int,
        attributesGenerator: (Int) -> Map<String, Any?> = { emptyMap() }
    ): List<T> {
        return (0 until count step chunkSize).flatMap { offset ->
            val size = minOf(chunkSize, count - offset)

            delegate.createBatch(size) { index ->
                attributesGenerator(offset + index)
            }
        }
    }
}

// 使用
val users = ChunkedBatchBuilder(UserFactory.builder, chunkSize = 500)
    .createBatch(10000)
// 10,000件を500件ずつ20回に分けてバッチINSERT
```

### ストリーミング処理

```kotlin
fun <T : Record> streamingBatch(
    count: Int,
    factory: FactoryBuilder<T>,
    processor: (T) -> Unit
) {
    val chunkSize = 1000

    (0 until count step chunkSize).forEach { offset ->
        val size = minOf(chunkSize, count - offset)

        val chunk = factory.createBatch(size) { index ->
            mapOf("index" to (offset + index))
        }

        chunk.forEach(processor)

        // チャンク処理後にGC
        System.gc()
    }
}

// 使用
streamingBatch(100000, UserFactory) { user ->
    println("Processed: ${user.id}")
}
```

### 並列バッチ生成

```kotlin
fun <T : Record> parallelBatch(
    count: Int,
    factory: FactoryBuilder<T>,
    parallelism: Int = Runtime.getRuntime().availableProcessors()
): List<T> {
    val chunkSize = count / parallelism

    return (0 until parallelism).toList().parallelStream()
        .flatMap { partition ->
            val start = partition * chunkSize
            val size = if (partition == parallelism - 1) {
                count - start
            } else {
                chunkSize
            }

            factory.createBatch(size) { index ->
                mapOf("partition" to partition, "index" to index)
            }.stream()
        }
        .collect(Collectors.toList())
}

// 使用
val users = parallelBatch(10000, UserFactory, parallelism = 4)
// 4スレッドで並列生成
```

## PreparedStatement再利用

### ステートメントキャッシュ

```kotlin
class CachedBatchBuilder<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>
) {
    private val preparedStatementCache = ConcurrentHashMap<String, PreparedStatement>()

    fun createBatch(count: Int): List<T> {
        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val sql = buildInsertSql(table)

        val preparedStatement = preparedStatementCache.getOrPut(sql) {
            dsl.connection { conn ->
                conn.prepareStatement(sql)
            }
        }

        // PreparedStatementを再利用してバッチ実行
        return executeBatch(preparedStatement, count)
    }

    private fun buildInsertSql(table: Table<T>): String {
        val fields = table.fields().joinToString { it.name }
        val placeholders = table.fields().joinToString { "?" }

        return "INSERT INTO ${table.name} ($fields) VALUES ($placeholders)"
    }
}
```

## メモリ管理

### ストリーム処理

```kotlin
class StreamingBatchBuilder<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>
) {
    fun createBatchStream(count: Int): Sequence<T> {
        return sequence {
            val chunkSize = 1000

            (0 until count step chunkSize).forEach { offset ->
                val size = minOf(chunkSize, count - offset)

                val chunk = createChunk(size, offset)

                yieldAll(chunk)
            }
        }
    }

    private fun createChunk(size: Int, offset: Int): List<T> {
        // チャンク単位でバッチINSERT
        return BatchBuilder(dsl, definition).createBatch(size) { index ->
            mapOf("globalIndex" to (offset + index))
        }
    }
}

// 使用
UserFactory.createBatchStream(100000)
    .chunked(1000)
    .forEach { chunk ->
        processChunk(chunk)
        // チャンク処理後にメモリ解放
    }
```

## エラーハンドリング

### 部分失敗の処理

```kotlin
class ResilientBatchBuilder<T : Record>(
    private val delegate: BatchBuilder<T>
) {
    fun createBatch(
        count: Int,
        onError: (Int, Exception) -> Unit = { _, _ -> }
    ): List<T> {
        val results = mutableListOf<T>()
        val chunkSize = 100

        (0 until count step chunkSize).forEach { offset ->
            val size = minOf(chunkSize, count - offset)

            try {
                val chunk = delegate.createBatch(size) { index ->
                    mapOf("offset" to offset, "index" to index)
                }
                results.addAll(chunk)
            } catch (e: Exception) {
                onError(offset, e)
                // 失敗したチャンクをスキップして続行
            }
        }

        return results
    }
}
```

### リトライ

```kotlin
class RetryableBatchBuilder<T : Record>(
    private val delegate: BatchBuilder<T>,
    private val maxRetries: Int = 3
) {
    fun createBatch(count: Int): List<T> {
        var attempts = 0

        while (attempts < maxRetries) {
            try {
                return delegate.createBatch(count)
            } catch (e: Exception) {
                attempts++
                if (attempts >= maxRetries) throw e

                Thread.sleep(1000 * attempts)
            }
        }

        throw IllegalStateException("Unreachable")
    }
}
```

## テスト例

### 基本的なテスト

```kotlin
@Test
fun `batch creation works`() {
    val users = UserFactory.createBatch(100)

    assertThat(users).hasSize(100)
    assertThat(users.map { it.id }).doesNotHaveDuplicates()
}
```

### パフォーマンステスト

```kotlin
@Test
fun `batch is faster than individual`() {
    val individualTime = measureTime {
        (1..100).forEach { UserFactory.create() }
    }

    val batchTime = measureTime {
        UserFactory.createBatch(100)
    }

    assertThat(batchTime).isLessThan(individualTime / 5)
}
```

### ストリーミングテスト

```kotlin
@Test
fun `streaming batch processes large dataset`() {
    val processed = mutableListOf<Long>()

    UserFactory.createBatchStream(10000)
        .chunked(1000)
        .forEach { chunk ->
            processed.addAll(chunk.map { it.id })
        }

    assertThat(processed).hasSize(10000)
}
```

## ベストプラクティス

### 1. 適切なチャンクサイズ

```kotlin
// Good: 適切なサイズ（1000-5000）
UserFactory.createBatch(1000)

// Bad: 大きすぎる（メモリ不足）
UserFactory.createBatch(1000000)

// Bad: 小さすぎる（効率悪い）
UserFactory.createBatch(10)
```

### 2. ストリーミング処理の活用

```kotlin
// Good: 大量データはストリーミング
UserFactory.createBatchStream(100000)
    .chunked(1000)
    .forEach { processChunk(it) }

// Bad: 全てメモリに載せる
val users = UserFactory.createBatch(100000)  // OutOfMemoryError
```

### 3. エラーハンドリング

```kotlin
// Good: エラー処理あり
try {
    UserFactory.createBatch(10000)
} catch (e: BatchInsertException) {
    logger.error("Batch insert failed", e)
    // フォールバック処理
}

// Bad: エラー無視
UserFactory.createBatch(10000)  // 失敗しても気づかない
```

## 制限事項

1. **コールバック**: afterCreateは全件実行（遅い可能性）
2. **トランザクション**: 全件成功か全件失敗（部分成功なし）
3. **ID生成**: 一部DBでAUTO_INCREMENTの取得が困難

## 今後の拡張

- **非同期バッチ**: CompletableFutureで非同期実行
- **プログレス通知**: 進捗状況の通知
- **並列度制御**: 自動的な最適化

## まとめ

Batch Operationsは以下を実現:
1. **高速化**: バッチINSERTで20-30倍高速
2. **メモリ効率**: ストリーミング処理対応
3. **スケーラビリティ**: 大量データ生成
4. **柔軟性**: チャンク分割、並列処理
