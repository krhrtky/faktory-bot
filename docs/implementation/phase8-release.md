# Phase 8: Release

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [Phase 7: Quality](./phase7-quality.md) - 前提Phase

## 期間
2週間

## 優先度
P1

## 目標
Maven Centralへの公開とエコシステム対応。

## 前提条件
Phase 7完了（品質ゲートクリア）

## 成果物
- Maven Central公開パッケージ
- Spring Boot Starter
- サンプルプロジェクト

## タスク一覧

### 1. パッケージング

#### 1.1 Maven Central公開準備

**build.gradle.kts設定**:
```kotlin
plugins {
    `maven-publish`
    signing
}

group = "com.example"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("Faktory Bot")
                description.set("Type-safe test data factory for jOOQ and Kotlin")
                url.set("https://github.com/example/faktory-bot")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("example")
                        name.set("Example Developer")
                        email.set("dev@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/example/faktory-bot.git")
                    developerConnection.set("scm:git:ssh://github.com/example/faktory-bot.git")
                    url.set("https://github.com/example/faktory-bot")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("ossrhUsername") as String?
                password = project.findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
```

#### 1.2 バージョニング戦略

**セマンティックバージョニング**:
- v0.1.0: MVP（P0, P1機能）
- v0.2.0: P2機能追加
- v1.0.0: 安定版
- v1.1.0: 新機能追加
- v2.0.0: 破壊的変更

**変更履歴管理**:
```markdown
# Changelog

## [0.1.0] - 2025-01-15

### Added
- 基本的なファクトリ定義DSL
- build()/create()メソッド
- シーケンス生成
- アソシエーション
- Transient属性
- トレイト
- コールバック

### Changed
- なし

### Fixed
- なし
```

#### 1.3 リリースノート作成
```markdown
# Release Notes v0.1.0

## Overview
Faktory Bot v0.1.0 is the first MVP release.

## Features
- Type-safe factory definition DSL
- Build strategies (build, create, buildList, createList)
- Sequence generation
- Association resolution
- Transient attributes
- Traits
- Callbacks

## Installation
```kotlin
dependencies {
    testImplementation("com.example:faktory-bot:0.1.0")
}
```

## Quick Start
See [README.md](README.md)

## Known Issues
- buildStubbed() は v2.0 で実装予定

## Migration Guide
初回リリースのため該当なし
```

#### 1.4 ライセンス選定

**MIT License**:
```
MIT License

Copyright (c) 2025 Example

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

...
```

### 2. エコシステム対応

#### 2.1 Spring Boot Starter作成

**プロジェクト構造**:
```
faktory-spring-boot-starter/
├── build.gradle.kts
└── src/
    └── main/
        ├── kotlin/
        │   └── com/example/faktory/spring/
        │       ├── FactoryAutoConfiguration.kt
        │       └── FactoryTransactionExtension.kt
        └── resources/
            └── META-INF/
                └── spring.factories
```

**AutoConfiguration**:
```kotlin
@Configuration
@ConditionalOnClass(DSLContext::class)
class FactoryAutoConfiguration {

    @Bean
    fun factoryTransactionManager(dsl: DSLContext): TransactionManager {
        return JooqTransactionManager(dsl)
    }

    @Bean
    fun factoryDslContextProvider(dsl: DSLContext): FactoryDslContextProvider {
        return FactoryDslContextProvider().apply {
            setDslContext(dsl)
        }
    }
}
```

**spring.factories**:
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.example.faktory.spring.FactoryAutoConfiguration
```

#### 2.2 Kotlin Multiplatform対応検討

**将来的な対応**:
- JVM: メイン実装
- JS: 将来対応検討
- Native: 将来対応検討

**現状**:
v0.1.0ではJVMのみ対応

#### 2.3 IDE plugin開発（IntelliJ IDEA）

**将来的な機能**:
- ファクトリ定義の補完
- ファクトリへのジャンプ
- リファクタリング対応

**現状**:
v0.1.0では未対応（v2.0以降で検討）

#### 2.4 サンプルプロジェクト作成

**リポジトリ構成**:
```
faktory-bot-examples/
├── spring-boot-example/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
│           └── FactoryExampleTest.kt
└── standalone-example/
    ├── build.gradle.kts
    └── src/
        └── test/kotlin/
            └── BasicFactoryTest.kt
```

**Spring Boot Example**:
```kotlin
@SpringBootTest
@Transactional
class UserServiceTest {

    @BeforeAll
    fun setupFactories() {
        factory<UserRecord> {
            name = "Test User"
            email = sequence { n -> "user${n}@example.com" }
        }

        factory<PostRecord> {
            title = "Test Post"
            user = association<UserRecord>()
        }
    }

    @Test
    fun `user can create post`() {
        val user = UserFactory.create()
        val post = PostFactory.create(userId = user.id)

        assertThat(post.userId).isEqualTo(user.id)
    }
}
```

### 3. リリースプロセス

#### 3.1 リリース前チェックリスト
- [ ] 全テストが成功
- [ ] カバレッジ90%以上
- [ ] KDocが完備
- [ ] README.mdが完成
- [ ] CHANGELOGが更新
- [ ] LICENSEが設定
- [ ] バージョン番号が正しい
- [ ] GPG署名設定完了

#### 3.2 リリース手順
1. タグ作成: `git tag -a v0.1.0 -m "Release v0.1.0"`
2. ビルド: `./gradlew clean build`
3. テスト: `./gradlew test`
4. 公開: `./gradlew publishToSonatype closeSonatypeStagingRepository`
5. タグプッシュ: `git push origin v0.1.0`
6. GitHub Release作成

#### 3.3 リリース後タスク
- [ ] Twitterで告知
- [ ] Kotlin Slackで告知
- [ ] ブログ記事執筆
- [ ] Awesome Kotlinへの登録申請

## マーケティング

### アナウンス文例
```
🎉 Faktory Bot v0.1.0 リリース！

jOOQ + Kotlinでの型安全なテストデータ生成ライブラリです。

✨ Features:
- 型安全なDSL
- シーケンス生成
- アソシエーション
- トレイト
- 自動ロールバック

📦 Installation:
testImplementation("com.example:faktory-bot:0.1.0")

🔗 https://github.com/example/faktory-bot
```

### ターゲット
- jOOQユーザー
- Kotlinユーザー
- テスト自動化に関心のある開発者
- Factory Bot経験者

## チェックリスト

### パッケージング
- [ ] Maven Central設定完了
- [ ] バージョニング戦略決定
- [ ] リリースノート作成
- [ ] ライセンス設定

### エコシステム
- [ ] Spring Boot Starter作成
- [ ] サンプルプロジェクト作成
- [ ] ドキュメント整備

### リリース
- [ ] リリース前チェックリスト確認
- [ ] Maven Central公開
- [ ] GitHub Release作成
- [ ] アナウンス

## 成功基準

- ✅ Maven Centralで公開
- ✅ Spring Boot Starterが動作
- ✅ サンプルプロジェクトが動作
- ✅ ドキュメントが完備
- ✅ アナウンス完了

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| Maven Central審査遅延 | 中 | 早めの申請 |
| GPG署名トラブル | 中 | 事前テスト |
| ドキュメント不備 | 低 | レビュー実施 |

## 次のステップ

v1.0.0に向けて:
- ファクトリ継承の完成度向上
- トランザクション管理の充実
- バッチ操作の最適化
- buildStubbed()実装（v2.0）
- IDE Plugin開発（v2.0）
