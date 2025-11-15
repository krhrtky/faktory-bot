# Phase 4: Extensions

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Traits](../features/traits.md) - トレイト機能
- [Callbacks](../features/callbacks.md) - コールバック
- [Inheritance](../features/inheritance.md) - ファクトリ継承
- [Phase 3: Essentials](./phase3-essentials.md) - 前提Phase

## 期間
3週間

## 優先度
P1-P2

## 目標
ファクトリの拡張機能（トレイト、コールバック、継承）を実装する。

## 前提条件
Phase 3完了

## 成果物
- トレイト機能実装
- コールバック機能実装
- ファクトリ継承実装

## タスク一覧

### 1. トレイト機能

#### 1.1 トレイト定義DSL
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

// DSL
fun FactoryDslBuilder<T>.trait(name: String, block: FactoryDslBuilder<T>.() -> Unit) {
    val traitBuilder = FactoryDslBuilder(recordClass)
    traitBuilder.block()
    traits[name] = TraitDefinition(
        name = name,
        attributes = traitBuilder.attributes,
        callbacks = traitBuilder.callbacks
    )
}
```

#### 1.2 トレイト適用メカニズム
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
```

#### 1.3 複数トレイトの合成
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
            }
        }
    }
}
```

#### 1.4 トレイト間の依存関係管理
```kotlin
class TraitDependencyResolver<T : Record> {
    private val dependencies = mutableMapOf<String, Set<String>>()

    fun addDependency(traitName: String, dependsOn: String) {
        dependencies[traitName] = dependencies.getOrDefault(traitName, emptySet()) + dependsOn
    }

    fun resolve(traitNames: List<String>): List<String> {
        val resolved = mutableListOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(name: String) {
            if (name in visited) return
            visited.add(name)

            dependencies[name]?.forEach { dep ->
                visit(dep)
            }

            resolved.add(name)
        }

        traitNames.forEach { visit(it) }

        return resolved
    }
}
```

### 2. コールバック

#### 2.1 afterBuild / beforeCreate / afterCreate
```kotlin
class DefaultCallbackRegistry<T : Record> : CallbackRegistry<T> {
    private val afterBuildCallbacks = mutableListOf<(T, TransientContext) -> Unit>()
    private val beforeCreateCallbacks = mutableListOf<(T, TransientContext) -> Unit>()
    private val afterCreateCallbacks = mutableListOf<(T, TransientContext) -> Unit>()

    override fun afterBuild(callback: (T) -> Unit) {
        afterBuildCallbacks.add { record, _ -> callback(record) }
    }

    override fun beforeCreate(callback: (T) -> Unit) {
        beforeCreateCallbacks.add { record, _ -> callback(record) }
    }

    override fun afterCreate(callback: (T) -> Unit) {
        afterCreateCallbacks.add { record, _ -> callback(record) }
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
}
```

#### 2.2 カスタムコールバック登録
```kotlin
interface CustomCallback<T : Record> {
    val name: String
    fun execute(record: T, context: CallbackContext)
}

class ExtensibleCallbackRegistry<T : Record> : CallbackRegistry<T> {
    private val customCallbacks = mutableMapOf<String, MutableList<CustomCallback<T>>>()

    fun registerCustom(callback: CustomCallback<T>) {
        customCallbacks.getOrPut(callback.name) { mutableListOf() }.add(callback)
    }

    fun executeCustom(name: String, record: T, context: CallbackContext) {
        customCallbacks[name]?.forEach { it.execute(record, context) }
    }
}
```

#### 2.3 コールバックチェーン
```kotlin
class CallbackChain<T : Record> {
    private val chain = mutableListOf<(T) -> Unit>()

    fun add(callback: (T) -> Unit) {
        chain.add(callback)
    }

    fun execute(record: T) {
        chain.forEach { it(record) }
    }

    fun executeSafely(record: T, onError: (Exception) -> Unit = {}) {
        chain.forEach { callback ->
            try {
                callback(record)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
```

#### 2.4 エラーハンドリング
```kotlin
class SafeCallbackRegistry<T : Record>(
    private val delegate: CallbackRegistry<T>,
    private val errorHandler: (Exception) -> Unit = { throw it }
) : CallbackRegistry<T> by delegate {

    override fun execute(
        phase: CallbackPhase,
        record: T,
        transients: TransientContext
    ) {
        try {
            delegate.execute(phase, record, transients)
        } catch (e: Exception) {
            errorHandler(e)
        }
    }
}
```

### 3. ファクトリ継承

#### 3.1 親ファクトリ参照
```kotlin
data class DefaultFactoryDefinition<T : Record>(
    override val recordClass: KClass<T>,
    override val name: String? = null,
    override val parent: FactoryDefinition<T>? = null,
    override val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    override val traits: Map<String, TraitDefinition<T>> = emptyMap(),
    override val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    override val transients: TransientDefinition = TransientDefinition()
) : FactoryDefinition<T>
```

#### 3.2 属性のオーバーライド
```kotlin
class FactoryResolver(private val registry: FactoryRegistry) {
    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T> {
        val chain = buildInheritanceChain(definition)
        return mergeChain(chain)
    }

    private fun <T : Record> buildInheritanceChain(
        definition: FactoryDefinition<T>
    ): List<FactoryDefinition<T>> {
        val chain = mutableListOf<FactoryDefinition<T>>()
        var current: FactoryDefinition<T>? = definition

        while (current != null) {
            chain.add(0, current)  // 親を先頭に
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

        // 親から順にマージ（子が上書き）
        chain.forEach { def ->
            mergedAttributes.putAll(def.attributes)
            mergedCallbacks.merge(def.callbacks)
            mergedTransients.merge(def.transients)
        }

        return ResolvedFactory(
            definition = chain.last(),
            mergedAttributes = mergedAttributes,
            mergedCallbacks = mergedCallbacks,
            mergedTransients = mergedTransients
        )
    }
}
```

#### 3.3 トレイトの継承
```kotlin
class TraitInheritanceResolver<T : Record> {
    fun resolve(
        definition: FactoryDefinition<T>
    ): Map<String, TraitDefinition<T>> {
        val mergedTraits = mutableMapOf<String, TraitDefinition<T>>()

        var current: FactoryDefinition<T>? = definition

        while (current != null) {
            mergedTraits.putAll(current.traits)
            current = current.parent
        }

        return mergedTraits
    }
}
```

#### 3.4 コールバックの継承
```kotlin
override fun merge(other: CallbackRegistry<T>): CallbackRegistry<T> {
    val merged = DefaultCallbackRegistry<T>()

    // 親のコールバックを先に追加
    merged.afterBuildCallbacks.addAll(this.afterBuildCallbacks)
    merged.beforeCreateCallbacks.addAll(this.beforeCreateCallbacks)
    merged.afterCreateCallbacks.addAll(this.afterCreateCallbacks)

    // 子のコールバックを後に追加
    if (other is DefaultCallbackRegistry) {
        merged.afterBuildCallbacks.addAll(other.afterBuildCallbacks)
        merged.beforeCreateCallbacks.addAll(other.beforeCreateCallbacks)
        merged.afterCreateCallbacks.addAll(other.afterCreateCallbacks)
    }

    return merged
}
```

## テスト実装

### トレイトのテスト
```kotlin
@Test
fun `trait overrides attributes`() {
    factory<UserRecord> {
        role = UserRole.MEMBER

        trait("admin") {
            role = UserRole.ADMIN
        }
    }

    val admin = UserFactory.create("admin")

    assertThat(admin.role).isEqualTo(UserRole.ADMIN)
}
```

### コールバックのテスト
```kotlin
@Test
fun `callbacks are executed in order`() {
    val order = mutableListOf<String>()

    factory<UserRecord> {
        afterBuild { order.add("afterBuild") }
        beforeCreate { order.add("beforeCreate") }
        afterCreate { order.add("afterCreate") }
    }

    UserFactory.create()

    assertThat(order).containsExactly("afterBuild", "beforeCreate", "afterCreate")
}
```

### 継承のテスト
```kotlin
@Test
fun `child inherits parent attributes`() {
    factory<UserRecord>("parent") {
        name = "Parent"
        age = 30
    }

    factory<UserRecord>("child", parent = "parent") {
        role = UserRole.ADMIN
    }

    val user = UserFactory.create("child")

    assertThat(user.name).isEqualTo("Parent")
    assertThat(user.age).isEqualTo(30)
    assertThat(user.role).isEqualTo(UserRole.ADMIN)
}
```

## チェックリスト

- [ ] トレイト定義DSL実装
- [ ] トレイト適用メカニズム実装
- [ ] 複数トレイト合成実装
- [ ] トレイト依存関係管理実装
- [ ] コールバックレジストリ実装
- [ ] カスタムコールバック実装
- [ ] コールバックチェーン実装
- [ ] エラーハンドリング実装
- [ ] ファクトリ継承実装
- [ ] 属性オーバーライド実装
- [ ] トレイト継承実装
- [ ] コールバック継承実装
- [ ] テストカバレッジ90%以上

## 成功基準

- ✅ トレイトが正常に動作
- ✅ コールバックが正常に動作
- ✅ 継承が正常に動作
- ✅ テストカバレッジ90%以上

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| トレイト合成の複雑さ | 中 | シンプルなマージ戦略 |
| コールバックのエラー | 高 | 適切なエラーハンドリング |
| 継承の深さ | 中 | 深さ制限の検討 |

## 次のPhase

[Phase 5: Performance](./phase5-performance.md) - パフォーマンス最適化
