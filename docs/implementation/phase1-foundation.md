# Phase 1: Foundation

## 関連ドキュメント
- [Sprint Planning](../planning/sprint-planning.md) - 全体スケジュール
- [System Design](../architecture/system-design.md) - アーキテクチャ設計
- [Core Interfaces](../architecture/core-interfaces.md) - インターフェース定義

## 期間
1週間

## 優先度
P0（必須）

## 目標
プロジェクトの基盤を構築し、開発環境を整備する。

## 前提条件
なし

## 成果物
- Gradleプロジェクト
- CI/CD設定
- コーディング規約
- アーキテクチャ設計ドキュメント

## タスク一覧

### 1. プロジェクトセットアップ

#### 1.1 Gradleプロジェクト構成
```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jooq.jooq-codegen-gradle") version "3.18.7"
    `maven-publish`
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // jOOQ
    implementation("org.jooq:jooq:3.18.7")
    implementation("org.jooq:jooq-kotlin:3.18.7")

    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}

tasks.test {
    useJUnitPlatform()
}
```

#### 1.2 ディレクトリ構造
```
faktory-bot/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/example/faktory/
│   │           ├── core/           # コア機能
│   │           ├── dsl/            # DSL定義
│   │           ├── builder/        # ビルダー
│   │           ├── registry/       # レジストリ
│   │           ├── sequence/       # シーケンス
│   │           └── jooq/           # jOOQ統合
│   └── test/
│       └── kotlin/
│           └── com/example/faktory/
│               ├── core/
│               └── integration/
├── docs/                          # ドキュメント（既に作成済み）
└── README.md
```

### 2. CI/CD環境構築

#### 2.1 GitHub Actions設定
```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: ~/.gradle/caches
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}

    - name: Build with Gradle
      run: ./gradlew build

    - name: Run tests
      run: ./gradlew test

    - name: Upload coverage
      uses: codecov/codecov-action@v3
```

#### 2.2 コードカバレッジ
```kotlin
// build.gradle.kts
plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/jooq/generated/**")
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
```

### 3. コーディング規約の策定

#### 3.1 Kotlin Coding Conventions
```kotlin
// .editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.kt]
indent_style = space
indent_size = 4
max_line_length = 120
```

#### 3.2 ktlint設定
```kotlin
// build.gradle.kts
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}
```

#### 3.3 detekt設定
```yaml
# detekt.yml
build:
  maxIssues: 0

complexity:
  LongMethod:
    threshold: 30
  ComplexMethod:
    threshold: 10

naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
```

### 4. アーキテクチャ設計

#### 4.1 コアインターフェースの定義
「[Core Interfaces](../architecture/core-interfaces.md)」を参照して以下を定義:
- FactoryDefinition
- AttributeDefinition
- FactoryRegistry
- FactoryBuilder
- SequenceManager

#### 4.2 jOOQ統合レイヤーの設計
「[jOOQ Integration](../architecture/jooq-integration.md)」を参照して設計:
- JooqTableResolver
- JooqRecordBuilder
- ForeignKeyResolver

#### 4.3 プラグインアーキテクチャの検討
```kotlin
interface FactoryPlugin {
    fun onFactoryRegister(definition: FactoryDefinition<*>)
    fun onBuild(record: Record)
    fun onCreate(record: Record)
}
```

#### 4.4 エラーハンドリング戦略の決定
```kotlin
sealed class FactoryException : RuntimeException()

class FactoryNotFound(recordClass: KClass<*>) : FactoryException()
class CircularAssociation(chain: List<KClass<*>>) : FactoryException()
class InvalidAttribute(name: String, reason: String) : FactoryException()
```

## チェックリスト

- [ ] Gradleプロジェクト作成
- [ ] 依存関係の設定
- [ ] ディレクトリ構造の確立
- [ ] GitHub Actions CI設定
- [ ] コードカバレッジ設定
- [ ] ktlint設定
- [ ] detekt設定
- [ ] .editorconfig作成
- [ ] コアインターフェース定義
- [ ] jOOQ統合設計
- [ ] エラーハンドリング戦略決定
- [ ] README.md作成

## 成功基準

- ✅ CI/CDが正常に動作する
- ✅ コーディング規約が適用される
- ✅ アーキテクチャドキュメントが完成
- ✅ 基本的なビルドが通る

## リスクと対策

| リスク | 影響度 | 対策 |
|-------|--------|------|
| Gradle設定の複雑さ | 中 | テンプレートの利用 |
| CI/CD環境のトラブル | 中 | ローカルでの事前検証 |
| アーキテクチャの変更 | 高 | 早期のレビューとフィードバック |

## 次のPhase

[Phase 2: Core](./phase2-core.md) - コア機能実装
