# jOOQ Integration

## jOOQ概要

### jOOQの特徴
- **型安全なSQL**: コンパイル時にSQL検証
- **コード生成**: DBスキーマからKotlinコード自動生成
- **DSL**: 流暢なSQL構築API
- **Record**: テーブル行を表す型安全なオブジェクト

### 生成されるクラス

```kotlin
// テーブル定義
object USER : Table<UserRecord>("user") {
    val ID = createField(DSL.name("id"), SQLDataType.BIGINT)
    val NAME = createField(DSL.name("name"), SQLDataType.VARCHAR)
    val EMAIL = createField(DSL.name("email"), SQLDataType.VARCHAR)
}

// レコードクラス
class UserRecord : TableRecord<UserRecord>(USER) {
    var id: Long?
    var name: String?
    var email: String?
}
```

## 統合戦略

### 1. Record生成

#### DSLContextとの連携

```kotlin
class JooqRecordBuilder<T : Record>(
    private val dsl: DSLContext,
    private val table: Table<T>
) : BuildStrategy {

    override fun <T : Record> build(
        recordClass: KClass<T>,
        attributes: Map<String, Any?>
    ): T {
        val record = dsl.newRecord(table)

        attributes.forEach { (name, value) ->
            val field = table.field(name) ?: throw IllegalArgumentException("Unknown field: $name")
            record.set(field as Field<Any>, value)
        }

        return record
    }

    override fun <T : Record> create(
        recordClass: KClass<T>,
        attributes: Map<String, Any?>,
        dsl: DSLContext
    ): T {
        val record = build(recordClass, attributes)
        record.store()  // jOOQのINSERT
        return record
    }
}
```

### 2. テーブル・フィールド解決

#### リフレクションによる解決

```kotlin
object JooqTableResolver {
    private val tableCache = ConcurrentHashMap<KClass<out Record>, Table<*>>()

    fun <T : Record> resolveTable(recordClass: KClass<T>): Table<T> {
        return tableCache.getOrPut(recordClass) {
            findTableCompanion(recordClass)
        } as Table<T>
    }

    private fun findTableCompanion(recordClass: KClass<out Record>): Table<*> {
        val tableName = recordClass.simpleName?.removeSuffix("Record")?.uppercase()
            ?: throw IllegalArgumentException("Cannot determine table name")

        val tablesClass = Class.forName("${recordClass.java.packageName}.Tables")
        val tableField = tablesClass.getField(tableName)
        return tableField.get(null) as Table<*>
    }
}
```

#### フィールド名のマッピング

```kotlin
object FieldNameMapper {
    fun toSnakeCase(camelCase: String): String =
        camelCase.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    fun toCamelCase(snakeCase: String): String =
        snakeCase.split("_")
            .mapIndexed { index, s ->
                if (index == 0) s else s.capitalize()
            }
            .joinToString("")
}
```

### 3. 主キー処理

#### 自動生成IDの取得

```kotlin
fun <T : Record> T.getId(): Long? {
    val table = JooqTableResolver.resolveTable(this::class)
    val primaryKey = table.primaryKey ?: return null
    val idField = primaryKey.fields.firstOrNull() ?: return null

    return this.get(idField as Field<Long>)
}

fun <T : Record> T.setId(id: Long?) {
    val table = JooqTableResolver.resolveTable(this::class)
    val primaryKey = table.primaryKey ?: return
    val idField = primaryKey.fields.firstOrNull() ?: return

    this.set(idField as Field<Long>, id)
}
```

### 4. 外部キー解決

#### ForeignKey情報の取得

```kotlin
data class ForeignKeyInfo(
    val sourceField: Field<*>,
    val targetTable: Table<*>,
    val targetField: Field<*>
)

object ForeignKeyResolver {
    fun <T : Record> resolveForeignKeys(table: Table<T>): List<ForeignKeyInfo> {
        return table.references.map { fk ->
            ForeignKeyInfo(
                sourceField = fk.fields.first(),
                targetTable = fk.key.table,
                targetField = fk.key.fields.first()
            )
        }
    }

    fun findForeignKey(table: Table<*>, fieldName: String): ForeignKeyInfo? {
        return resolveForeignKeys(table).find {
            it.sourceField.name.equals(fieldName, ignoreCase = true)
        }
    }
}
```

#### アソシエーションでの活用

```kotlin
class JooqAssociationResolver(
    private val factoryRegistry: FactoryRegistry,
    private val dsl: DSLContext
) : AssociationResolver {

    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext
    ): T {
        val factory = factoryRegistry.find(association.targetClass, association.factoryName)
        val builder = FactoryBuilder(factory, dsl, context.sequenceManager, this)

        val record = if (context.isCreate) {
            builder.create(association.overrides)
        } else {
            builder.build(association.overrides)
        }

        return record
    }
}
```

### 5. バッチ挿入最適化

#### jOOQのbatchInsert活用

```kotlin
class JooqBatchBuilder<T : Record>(
    private val dsl: DSLContext,
    private val table: Table<T>
) {
    fun createBatch(
        records: List<T>
    ): List<T> {
        if (records.isEmpty()) return emptyList()

        dsl.batchInsert(records).execute()

        return records
    }

    fun createBatch(
        count: Int,
        attributesGenerator: (Int) -> Map<String, Any?>
    ): List<T> {
        val records = (1..count).map { index ->
            val attributes = attributesGenerator(index)
            buildRecord(attributes)
        }

        return createBatch(records)
    }

    private fun buildRecord(attributes: Map<String, Any?>): T {
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

### 6. トランザクション管理

#### jOOQのトランザクションAPI

```kotlin
class JooqTransactionManager(
    private val dsl: DSLContext
) : TransactionManager {

    override fun <T> withTransaction(block: () -> T): T {
        return dsl.transactionResult { config ->
            val transactionalDsl = DSL.using(config)
            block()
        }
    }

    override fun <T> withRollback(block: () -> T): T {
        return dsl.transactionResult { config ->
            try {
                val result = block()
                throw RollbackException()
            } catch (e: RollbackException) {
                null as T
            }
        }
    }

    private class RollbackException : RuntimeException()

    override fun begin() {
        throw UnsupportedOperationException("Use withTransaction instead")
    }

    override fun commit() {
        throw UnsupportedOperationException("Use withTransaction instead")
    }

    override fun rollback() {
        throw UnsupportedOperationException("Use withTransaction instead")
    }
}
```

### 7. DSLContext管理

#### Factory毎のDSLContext

```kotlin
class FactoryDslContextProvider {
    private val threadLocalDsl = ThreadLocal<DSLContext>()

    fun setDslContext(dsl: DSLContext) {
        threadLocalDsl.set(dsl)
    }

    fun getDslContext(): DSLContext =
        threadLocalDsl.get() ?: throw IllegalStateException("DSLContext not set")

    fun clear() {
        threadLocalDsl.remove()
    }
}

// 使用例
@BeforeEach
fun setup() {
    val dsl = DSL.using(connection, SQLDialect.POSTGRES)
    FactoryDslContextProvider().setDslContext(dsl)
}
```

### 8. データベース方言対応

#### マルチDB対応

```kotlin
enum class SupportedDialect(val jooqDialect: SQLDialect) {
    POSTGRES(SQLDialect.POSTGRES),
    MYSQL(SQLDialect.MYSQL),
    H2(SQLDialect.H2);

    companion object {
        fun detect(dsl: DSLContext): SupportedDialect {
            return when (dsl.dialect()) {
                SQLDialect.POSTGRES -> POSTGRES
                SQLDialect.MYSQL -> MYSQL
                SQLDialect.H2 -> H2
                else -> throw UnsupportedOperationException("Unsupported dialect: ${dsl.dialect()}")
            }
        }
    }
}

class DialectSpecificBuilder(
    private val dialect: SupportedDialect
) {
    fun buildSequenceQuery(sequenceName: String): String {
        return when (dialect) {
            SupportedDialect.POSTGRES -> "SELECT nextval('$sequenceName')"
            SupportedDialect.MYSQL -> "SELECT NEXT VALUE FOR $sequenceName"
            SupportedDialect.H2 -> "SELECT $sequenceName.nextval"
        }
    }
}
```

### 9. 型変換

#### KotlinとjOOQの型マッピング

```kotlin
object TypeConverter {
    fun convertToJooq(value: Any?, field: Field<*>): Any? {
        return when {
            value == null -> null
            field.dataType.type.isAssignableFrom(value::class.java) -> value
            field.dataType.type == LocalDateTime::class.java && value is String ->
                LocalDateTime.parse(value)
            field.dataType.type == Long::class.java && value is Int ->
                value.toLong()
            else -> value
        }
    }

    fun convertFromJooq(value: Any?, targetType: KClass<*>): Any? {
        return when {
            value == null -> null
            targetType.java.isAssignableFrom(value::class.java) -> value
            targetType == String::class && value is LocalDateTime ->
                value.toString()
            else -> value
        }
    }
}
```

## 実装例

### 完全な統合例

```kotlin
class JooqFactoryBuilder<T : Record>(
    private val definition: FactoryDefinition<T>,
    private val dsl: DSLContext,
    private val sequenceManager: SequenceManager,
    private val associationResolver: AssociationResolver
) : FactoryBuilder<T> {

    private val table = JooqTableResolver.resolveTable(definition.recordClass)

    override fun build(overrides: Map<String, Any?>): T {
        val mergedAttributes = definition.attributes + overrides.mapValues { StaticAttribute(it.value) }

        val context = EvaluationContext(
            sequenceManager = sequenceManager,
            associationResolver = associationResolver,
            transients = TransientContext(),
            attributeName = "",
            isCreate = false
        )

        val evaluatedAttributes = mergedAttributes.mapValues { (name, attr) ->
            attr.evaluate(context.withAttributeName(name))
        }

        val record = dsl.newRecord(table)

        evaluatedAttributes.forEach { (name, value) ->
            val field = table.field(FieldNameMapper.toSnakeCase(name))
            if (field != null) {
                val convertedValue = TypeConverter.convertToJooq(value, field)
                record.set(field as Field<Any>, convertedValue)
            }
        }

        definition.callbacks.execute(CallbackPhase.AFTER_BUILD, record)

        return record
    }

    override fun create(overrides: Map<String, Any?>): T {
        val record = build(overrides)

        definition.callbacks.execute(CallbackPhase.BEFORE_CREATE, record)

        record.store()

        definition.callbacks.execute(CallbackPhase.AFTER_CREATE, record)

        return record
    }

    override fun createList(count: Int, overrides: Map<String, Any?>): List<T> {
        return (1..count).map { create(overrides) }
    }
}
```

## パフォーマンス最適化

### 1. prepared statementのキャッシュ
- jOOQの設定で有効化: `Settings().withRenderFormatted(false)`

### 2. バッチ挿入
- `createList()`で`batchInsert()`を使用

### 3. 遅延ロード
- アソシエーションは必要時のみ解決

## まとめ

jOOQ統合により実現する機能:
1. **型安全性**: jOOQの型チェックを活用
2. **スキーマ追従**: コード生成で自動追従
3. **パフォーマンス**: バッチ操作、prepared statement
4. **マルチDB**: jOOQの方言機能を活用
5. **トランザクション**: jOOQのトランザクションAPIを活用
