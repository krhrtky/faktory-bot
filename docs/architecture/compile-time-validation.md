# コンパイル時検証の実装案

## 前提：テスト専用ライブラリとしての性質

Faktory Botは**テストデータ生成専用**のため、実行時エラーでも大きな問題はない：

- ✅ テスト実行時に即座にエラー検出
- ✅ CI/CDで自動的にキャッチ
- ✅ 本番コードに影響なし（testImplementation依存）
- ✅ 明確なエラーメッセージ

**結論**: コンパイル時検証は**必須ではない**

## 課題

現在のDSL設計では、必須フィールドの設定はコンパイル時に検証できず、実行時エラーとなる：

```kotlin
factory<UsersRecord> {
    USERS.NAME set "User"
    // USERS.EMAIL が未設定 → 実行時エラー
}
```

テスト専用ライブラリとしては許容範囲だが、より早期に検出したい場合の選択肢を示す。

## 解決策の比較

### 1. Detektカスタムルール

**実装難易度**: 中〜高
**効果**: 低〜中
**メンテナンスコスト**: 高

```kotlin
// detekt/src/main/kotlin/RequiredFactoryFieldsRule.kt
class RequiredFactoryFieldsRule : Rule() {
    override fun visitCallExpression(expression: KtCallExpression) {
        if (expression.isFactoryDefinition()) {
            val recordType = expression.getRecordType()
            // ❌ 問題: jOOQメタデータにアクセスできない
            val requiredFields = getRequiredFields(recordType)
            val setFields = expression.getSetFields()

            val missing = requiredFields - setFields
            if (missing.isNotEmpty()) {
                report(CodeSmell(
                    "Missing required fields: ${missing.joinToString()}"
                ))
            }
        }
    }
}
```

**欠点**（実現困難な理由）:
- ❌ **jOOQメタデータにアクセス不可**: `nullable(false)` は実行時情報
- ❌ **型情報だけでは判別不可**: `String?` が nullable か non-null かは型パラメータから不明
- ❌ 動的な評価（trait、sequenceなど）の解析が複雑
- ❌ KSPと組み合わせるなら、直接型安全ファクトリー生成する方が良い

**利点**:
- CI/CDで自動実行可能
- 既存コードの変更不要（実装できれば）

**結論**: Detekt単体では実用的な検証は困難

### 2. KSPベースの型安全ファクトリー生成

**実装難易度**: 高
**効果**: 非常に高
**メンテナンスコスト**: 中

```kotlin
// KSPが生成するコード
class UsersRecordFactoryBuilder {
    private var name: String? = null
    private var email: String? = null  // 必須
    private var age: Int? = null

    fun build(): Result {
        require(email != null) { "email is required" }
        // コンパイラが email の null チェックを強制
    }
}
```

**利点**:
- 完全な型安全性
- IDEの補完サポート
- コンパイル時エラー

**欠点**:
- コード生成の複雑さ
- DSLの柔軟性が低下
- ビルド時間の増加

### 3. Phantom Types パターン

**実装難易度**: 非常に高
**効果**: 高
**メンテナンスコスト**: 高

```kotlin
sealed interface EmailSet
object EmailNotSet : EmailSet
object EmailIsSet : EmailSet

class FactoryBuilder<T : Record, E : EmailSet> {
    fun email(value: String): FactoryBuilder<T, EmailIsSet> = ...

    // EmailIsSet の場合のみ build() が呼べる
    fun build(): T where E : EmailIsSet = ...
}
```

**利点**:
- 完全な型安全性
- コンパイル時エラー

**欠点**:
- 実装が非常に複雑
- DSL の使い勝手が悪化
- 型パラメータの爆発

### 4. IntelliJ IDEA Plugin

**実装難易度**: 非常に高
**効果**: 高
**メンテナンスコスト**: 高

- リアルタイムのコード検証
- IDEとの深い統合

**欠点**:
- 開発コストが非常に高い
- CI/CDでは動作しない
- 保守が大変

## 推奨アプローチ

### 現状維持: 実行時検証（推奨）

**テスト専用ライブラリとして、現状の実行時エラーで十分**

理由：
- ✅ テスト実行時に即座に検出される
- ✅ エラーメッセージが明確で修正しやすい
- ✅ CI/CDで自動的にキャッチ
- ✅ 本番コードに影響なし
- ✅ 実装コスト不要

```kotlin
@Test
fun `create user`() {
    factory<UsersRecord> {
        USERS.NAME set "User"
        // EMAIL 未設定
    }
    dsl.factory<UsersRecord>().build()
    // ↓ 即座にエラー
    // MissingRequiredAttributesException:
    // Missing required attributes for table 'users': email
}
```

### 代替案（将来的な検討）

#### オプション1: KSP型安全ファクトリー（必要に応じて）

**採用条件**:
- 大規模プロジェクトでの利用が増加
- ユーザーから型安全性の要望が多い
- メンテナンスリソースが確保できる

**実装方針**:
- DSL版と**共存**（置き換えではない）
- ユーザーが用途に応じて選択

```kotlin
// DSL版（柔軟性重視） - デフォルト
factory<UsersRecord> {
    USERS.NAME set "User"
    USERS.EMAIL set "user@example.com"
}

// KSP版（型安全性重視） - オプション
@GenerateFactory
data class User(
    val email: String,  // NOT NULL
    val name: String = "User",
    val age: Int? = null
)

UsersFactory.build {
    email = "user@example.com"  // 必須（コンパイル時チェック）
}
```

#### オプション2: IDE Plugin（長期的な検討）

- 大規模採用後のみ検討
- リアルタイムフィードバック
- 開発コストが非常に高い

## 実装優先度

1. ✅ **実行時エラーメッセージの改善**（完了）← **現在のアプローチで十分**
2. ⬜ KSPオプション機能（将来的に検討、必須ではない）
3. ⬜ IDE Plugin（長期的な検討、優先度低）
4. ❌ Detektカスタムルール（実現困難、推奨しない）
