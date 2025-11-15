# Faktory Bot ドキュメント

jOOQ Fixture Library（Faktory Bot）の設計・実装ドキュメント一覧。

## 📋 目次

### プランニング

#### [Product Vision](./planning/product-vision.md)
プロダクトのビジョン、解決する課題、期待される効果

**主要トピック**:
- 現状の問題点（テストデータ作成の冗長性、型安全性の欠如等）
- 解決方法（DSLベース、jOOQネイティブ統合、宣言的定義）
- 期待される効果（開発効率50-70%向上）

#### [Product Backlog](./planning/product-backlog.md)
実装する全機能の優先順位付きリスト

**優先度マトリクス**:
- P0: Factory DSL、build/create、シーケンス
- P1: アソシエーション、Transient、トレイト、コールバック
- P2: 継承、トランザクション、バッチ操作
- P3: buildStubbed

#### [Sprint Planning](./planning/sprint-planning.md)
Phase別の実装計画とマイルストーン

**リリース計画**:
- MVP (v0.1.0): Phase 1-4（8週間）
- v1.0.0: Phase 5-7（6週間）
- v1.1.0以降: Phase 8

---

### アーキテクチャ

#### [System Design](./architecture/system-design.md)
システム全体のアーキテクチャ設計

**レイヤー構造**:
```
User Test Code
    ↓
Factory DSL Layer
    ↓
Factory Registry Layer
    ↓
Builder Layer
    ↓
jOOQ Integration Layer
    ↓
Database
```

**コアコンポーネント**:
- FactoryDefinition
- FactoryRegistry
- FactoryBuilder
- SequenceManager
- AssociationResolver
- CallbackRegistry
- TransactionManager

#### [Core Interfaces](./architecture/core-interfaces.md)
コアインターフェースの詳細定義

**主要インターフェース**:
- `FactoryDefinition<T>`: ファクトリ定義
- `AttributeDefinition<T>`: 属性定義（Static, Dynamic, Sequence, Association）
- `FactoryBuilder<T>`: build/createメソッド
- `SequenceManager`: シーケンス管理
- `AssociationResolver`: 関連解決

#### [jOOQ Integration](./architecture/jooq-integration.md)
jOOQとの統合設計

**統合戦略**:
- Record生成（DSLContext連携）
- テーブル・フィールド解決
- 主キー処理
- 外部キー解決
- バッチ挿入最適化
- トランザクション管理

---

### 機能詳細

#### コア機能（P0）

##### [Factory DSL](./features/factory-dsl.md)
宣言的で型安全なファクトリ定義DSL

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}
```

##### [Build Strategies](./features/build-strategies.md)
用途に応じた生成戦略

| 戦略 | DB永続化 | 使用場面 |
|-----|---------|---------|
| build() | なし | バリデーションテスト |
| create() | あり | 統合テスト |
| buildStubbed() | なし | 単体テスト |
| attributes() | なし | APIテスト |

##### [Sequences](./features/sequences.md)
一意な値の自動生成

```kotlin
email = sequence { n -> "user${n}@example.com" }
code = sequence("USER") { n -> "USR${n.toString().padStart(5, '0')}" }
```

#### 必須機能（P1）

##### [Associations](./features/associations.md)
関連レコードの自動生成

```kotlin
factory<PostRecord> {
    title = "Post"
    user = association<UserRecord>()
}
```

**特徴**:
- 自動解決
- 循環参照検出
- カスタマイズ可能

##### [Transients](./features/transients.md)
非永続化パラメータ

```kotlin
transient {
    val postsCount = 5
}

afterCreate { user, transients ->
    PostFactory.createList(transients.postsCount, userId = user.id)
}
```

##### [Traits](./features/traits.md)
ファクトリのバリエーション

```kotlin
trait("admin") {
    role = UserRole.ADMIN
}

trait("with_posts") {
    afterCreate { user ->
        PostFactory.createList(5, userId = user.id)
    }
}
```

##### [Callbacks](./features/callbacks.md)
ライフサイクルフック

```kotlin
afterBuild { user -> logger.debug("Built: ${user.id}") }
beforeCreate { user -> user.createdAt = LocalDateTime.now() }
afterCreate { user -> AuditLogFactory.create(targetId = user.id) }
```

#### 拡張機能（P2）

##### [Inheritance](./features/inheritance.md)
ファクトリ定義の継承

```kotlin
factory<UserRecord>("base") {
    name = "User"
}

factory<UserRecord>("admin", parent = "base") {
    role = UserRole.ADMIN
}
```

##### [Transactions](./features/transactions.md)
テストの独立性保証

```kotlin
@Test
fun test() = withFactoryTransaction {
    val user = UserFactory.create()
    // 自動ロールバック
}
```

##### [Batch Operations](./features/batch-operations.md)
大量データの高速生成

```kotlin
val users = UserFactory.createBatch(1000)
// 個別INSERTの20-30倍高速
```

---

### 実装計画

#### [Phase 1: Foundation](./implementation/phase1-foundation.md)
**期間**: 1週間 | **優先度**: P0

**成果物**:
- Gradleプロジェクト
- CI/CD設定（GitHub Actions）
- コーディング規約（ktlint, detekt）
- アーキテクチャ設計ドキュメント

#### [Phase 2: Core](./implementation/phase2-core.md)
**期間**: 2週間 | **優先度**: P0

**成果物**:
- FactoryRegistry実装
- Factory DSL実装
- build()/create()メソッド
- 基本的なテストスイート

#### [Phase 3: Essentials](./implementation/phase3-essentials.md)
**期間**: 2週間 | **優先度**: P0-P1

**成果物**:
- シーケンス機能実装
- アソシエーション機能実装
- Transient属性実装
- 統合テスト（PostgreSQL, MySQL, H2）

#### [Phase 4: Extensions](./implementation/phase4-extensions.md)
**期間**: 3週間 | **優先度**: P1-P2

**成果物**:
- トレイト機能実装
- コールバック機能実装
- ファクトリ継承実装

#### [Phase 5: Performance](./implementation/phase5-performance.md)
**期間**: 1週間 | **優先度**: P2

**成果物**:
- バッチ挿入最適化
- PreparedStatement再利用
- buildStubbed実装

#### [Phase 6: Transactions](./implementation/phase6-transactions.md)
**期間**: 1週間 | **優先度**: P2

**成果物**:
- 自動ロールバック機能
- トランザクション境界設定
- ネストされたトランザクション対応

#### [Phase 7: Quality](./implementation/phase7-quality.md)
**期間**: 2週間 | **優先度**: P0

**成果物**:
- テストスイート（90%以上カバレッジ）
- APIドキュメント（KDoc）
- 使用例集
- バグ修正

**品質ゲート**:
- ✅ テストカバレッジ90%以上
- ✅ 全テスト成功（単体・統合・パフォーマンス）
- ✅ KDoc完備
- ✅ 既知のP0バグゼロ

#### [Phase 8: Release](./implementation/phase8-release.md)
**期間**: 2週間 | **優先度**: P1

**成果物**:
- Maven Central公開パッケージ
- Spring Boot Starter
- サンプルプロジェクト

**リリースプロセス**:
1. Maven Central公開
2. GitHub Release作成
3. アナウンス（Twitter, Kotlin Slack）

---

## 📊 ドキュメント依存関係

### プランニング層
```
Product Vision
    ↓
Product Backlog
    ↓
Sprint Planning
```

### アーキテクチャ層
```
System Design
    ├─ Core Interfaces
    └─ jOOQ Integration
```

### 機能層
```
Factory DSL (基盤)
    ├─ Build Strategies
    ├─ Sequences
    ├─ Associations (→ Build Strategies)
    ├─ Transients (→ Callbacks)
    ├─ Traits (→ Callbacks)
    ├─ Callbacks
    ├─ Inheritance (→ Traits, Callbacks)
    ├─ Transactions (→ Build Strategies)
    └─ Batch Operations (→ Build Strategies)
```

### 実装層
```
Phase 1 (Foundation)
    ↓
Phase 2 (Core) → Factory DSL, Build Strategies
    ↓
Phase 3 (Essentials) → Sequences, Associations, Transients
    ↓
Phase 4 (Extensions) → Traits, Callbacks, Inheritance
    ↓
Phase 5 (Performance) → Batch Operations
    ↓
Phase 6 (Transactions) → Transactions
    ↓
Phase 7 (Quality)
    ↓
Phase 8 (Release)
```

---

## 🔍 クイックリンク

### 初めての方へ
1. [Product Vision](./planning/product-vision.md) - プロジェクトの概要
2. [Factory DSL](./features/factory-dsl.md) - 基本的な使い方
3. [Phase 1: Foundation](./implementation/phase1-foundation.md) - 実装の始め方

### 設計者向け
1. [System Design](./architecture/system-design.md) - アーキテクチャ全体像
2. [Core Interfaces](./architecture/core-interfaces.md) - インターフェース定義
3. [Product Backlog](./planning/product-backlog.md) - 優先順位

### 実装者向け
1. [Phase 2: Core](./implementation/phase2-core.md) - コア機能実装
2. [Phase 3: Essentials](./implementation/phase3-essentials.md) - 必須機能実装
3. [jOOQ Integration](./architecture/jooq-integration.md) - jOOQ統合

---

## 📝 表記規則

### ファイル間リンク
- 相対パス使用: `[Factory DSL](./features/factory-dsl.md)`
- アンカーリンク: `[System Design](../architecture/system-design.md)`

### コードブロック
```kotlin
// Kotlin コード例
factory<UserRecord> {
    name = "User"
}
```

### 依存関係表記
- **依存元**: このドキュメントを前提とする機能
- **依存先**: このドキュメントが依存する機能

---

## 🔄 更新履歴

### 2025-01-15
- 全ドキュメント初版作成
- ドキュメント構造確立
- 依存関係の明示化

---

## 📞 問い合わせ

ドキュメントに関する質問やフィードバックは、GitHubのIssueにて受け付けています。
