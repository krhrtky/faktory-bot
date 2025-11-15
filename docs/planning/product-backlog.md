# Product Backlog

## 関連ドキュメント
- [Product Vision](./product-vision.md) - プロダクトビジョン
- [Sprint Planning](./sprint-planning.md) - Sprint別実装計画
- Features配下の各機能詳細設計

## 優先度マトリクス

| 機能 | 重要度 | 緊急度 | 実装優先度 | 詳細ドキュメント |
|------|--------|--------|-----------|----------------|
| 基本的なファクトリ定義 | ⭐⭐⭐ | ⭐⭐⭐ | P0 | [Factory DSL](../features/factory-dsl.md) |
| build/create | ⭐⭐⭐ | ⭐⭐⭐ | P0 | [Build Strategies](../features/build-strategies.md) |
| シーケンス | ⭐⭐⭐ | ⭐⭐⭐ | P0 | [Sequences](../features/sequences.md) |
| アソシエーション | ⭐⭐⭐ | ⭐⭐ | P1 | [Associations](../features/associations.md) |
| Transient属性 | ⭐⭐ | ⭐⭐⭐ | P1 | [Transients](../features/transients.md) |
| トレイト | ⭐⭐ | ⭐⭐ | P1 | [Traits](../features/traits.md) |
| コールバック | ⭐⭐ | ⭐⭐ | P1 | [Callbacks](../features/callbacks.md) |
| ファクトリ継承 | ⭐⭐ | ⭐⭐ | P2 | [Inheritance](../features/inheritance.md) |
| トランザクション管理 | ⭐⭐ | ⭐ | P2 | [Transactions](../features/transactions.md) |
| バッチ操作 | ⭐⭐ | ⭐ | P2 | [Batch Operations](../features/batch-operations.md) |
| buildStubbed | ⭐ | ⭐ | P3 | [Build Strategies](../features/build-strategies.md) |

## コア機能 (P0)

### 1. ファクトリ定義DSL
**依存**: なし
**提供**: ファクトリ定義の基盤

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
    createdAt = { LocalDateTime.now() }
}
```

### 2. ビルド戦略
**依存**: ファクトリ定義DSL
**提供**: オブジェクト生成・永続化

| 戦略 | 説明 | 使用場面 |
|-----|------|---------|
| `build()` | オブジェクト生成のみ | バリデーションテスト |
| `create()` | DB永続化込み | 統合テスト |
| `buildStubbed()` | モック化されたオブジェクト | 単体テスト |
| `attributes()` | Map形式で属性取得 | APIテスト |

### 3. シーケンス生成
**依存**: ファクトリ定義DSL
**提供**: 一意な値の自動生成

```kotlin
sequence<Int> { n -> n }
sequence<String>("email") { n -> "user${n}@example.com" }

factory<UserRecord> {
    code = sequence("USER") { n -> "USR${n.toString().padStart(5, '0')}" }
}
```

## 必須機能 (P1)

### 4. アソシエーション管理
**依存**: ファクトリ定義DSL, ビルド戦略
**提供**: 関連レコードの自動生成

```kotlin
factory<PostRecord> {
    title = "Post Title"
    user = association<UserRecord>()

    author = association<UserRecord> {
        name = "Author Name"
    }
}
```

### 5. Transient属性
**依存**: ファクトリ定義DSL, コールバック
**提供**: テスト用パラメータの受け渡し

```kotlin
factory<UserRecord> {
    transient {
        val skipValidation = false
        val postsCount = 5
    }

    afterCreate { user, transients ->
        PostFactory.createList(transients.postsCount, userId = user.id)
    }
}
```

### 6. トレイト（バリエーション）
**依存**: ファクトリ定義DSL
**提供**: ファクトリのバリエーション定義

```kotlin
factory<UserRecord> {
    name = "User"

    trait("admin") {
        role = UserRole.ADMIN
        permissions = listOf("all")
    }

    trait("with_posts") {
        afterCreate { user ->
            PostFactory.createList(3, userId = user.id)
        }
    }
}
```

### 7. コールバック
**依存**: ファクトリ定義DSL, ビルド戦略
**提供**: ライフサイクルフック

```kotlin
factory<UserRecord> {
    afterBuild { user ->
        logger.debug("Built user: ${user.id}")
    }

    beforeCreate { user ->
        user.createdAt = LocalDateTime.now()
    }

    afterCreate { user ->
        AuditLogFactory.create(targetId = user.id)
    }
}
```

## 拡張機能 (P2)

### 8. ファクトリ継承
**依存**: ファクトリ定義DSL, トレイト
**提供**: ファクトリの再利用

```kotlin
factory<UserRecord>("user") {
    name = "Basic User"
    email = sequence { n -> "user${n}@example.com" }
}

factory<UserRecord>("admin", parent = "user") {
    role = UserRole.ADMIN
    permissions = listOf("all")
}
```

### 9. トランザクション管理
**依存**: ビルド戦略
**提供**: テストの独立性保証

```kotlin
@Test
fun testWithRollback() = withFactoryTransaction {
    val user = UserFactory.create()
    val posts = PostFactory.createList(10, userId = user.id)

    assertThat(posts).hasSize(10)
}
```

### 10. バッチ操作最適化
**依存**: ビルド戦略
**提供**: 大量データ生成の効率化

```kotlin
UserFactory.createBatch(1000) { index ->
    name = "User $index"
    email = "user${index}@example.com"
}
```

## MVP範囲

MVPでは **P0とP1の機能** を実装:
- ファクトリ定義DSL
- ビルド戦略（build/createのみ）
- シーケンス生成
- アソシエーション管理
- Transient属性
- トレイト
- コールバック

## 今後のバージョン

- **v1.1**: ファクトリ継承 (P2)
- **v1.2**: トランザクション管理 (P2)
- **v1.3**: バッチ操作最適化 (P2)
- **v2.0**: buildStubbed、IDE Plugin (P3)
