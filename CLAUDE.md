# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Faktory Bot is a type-safe test data factory library for jOOQ and Kotlin, inspired by Ruby's Factory Bot. It provides a declarative DSL for defining and generating test data with full type safety and jOOQ integration.

**Status**: Currently in planning/documentation phase. No implementation code exists yet.

## Architecture

The system is designed with a layered architecture (top to bottom):

1. **Factory DSL Layer**: User-facing DSL for defining factories declaratively
2. **Factory Registry Layer**: Factory registration, lookup, and inheritance resolution
3. **Builder Layer**: Record building with build/create strategies, association resolution, callbacks
4. **jOOQ Integration Layer**: Record generation, DB persistence, transaction management
5. **Database**: PostgreSQL, MySQL, H2 support

### Core Components

- **FactoryDefinition**: Immutable factory definitions with attributes, traits, callbacks
- **FactoryRegistry**: Thread-safe registry for factory lookup and resolution
- **FactoryBuilder**: Build strategies (build, create, buildList, createList, attributes)
- **SequenceManager**: Thread-safe sequence generation for unique values
- **AssociationResolver**: Automatic related record generation with circular reference detection
- **CallbackRegistry**: Lifecycle hooks (afterBuild, beforeCreate, afterCreate)
- **TransactionManager**: Automatic rollback for test isolation

See `docs/architecture/system-design.md` for detailed architecture.

## Development Commands

### Project Setup (Phase 1)
```bash
# Initialize Gradle project
./gradlew init --type kotlin-library

# Build
./gradlew build

# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Code Quality
```bash
# Lint
./gradlew ktlintCheck

# Auto-format
./gradlew ktlintFormat

# Static analysis
./gradlew detekt

# All quality checks
./gradlew ktlintCheck detekt test jacocoTestCoverageVerification
```

### Running Specific Tests
```bash
# Single test class
./gradlew test --tests FactoryRegistryTest

# Single test method
./gradlew test --tests FactoryRegistryTest."register and find factory"

# Test package
./gradlew test --tests com.example.faktory.core.*

# Integration tests only
./gradlew test --tests *IntegrationTest
```

### Database Tests
```bash
# PostgreSQL integration tests
./gradlew test --tests *PostgreSQLIntegrationTest

# MySQL integration tests
./gradlew test --tests *MySQLIntegrationTest

# H2 integration tests
./gradlew test --tests *H2IntegrationTest

# All DB integration tests
./gradlew integrationTest
```

## Implementation Phases

The project follows an 8-phase implementation plan. Current phase documentation:

### Phase 1: Foundation (1 week)
- Gradle project setup with Kotlin 1.9.22, jOOQ 3.18.7
- GitHub Actions CI/CD
- Code quality tools (ktlint, detekt, jacoco)
- Directory structure: `core/`, `dsl/`, `builder/`, `registry/`, `sequence/`, `jooq/`

**Key files to create**:
- `build.gradle.kts`: Dependencies and build configuration
- `.github/workflows/ci.yml`: CI pipeline
- `.editorconfig`: Code formatting
- `detekt.yml`: Static analysis rules

See `docs/implementation/phase1-foundation.md`

### Phase 2: Core (2 weeks)
- FactoryRegistry implementation
- Factory DSL (FactoryDslBuilder)
- build()/create() methods
- Basic test suite

**Key interfaces**:
- `FactoryDefinition<T : Record>`
- `FactoryBuilder<T : Record>`
- `AttributeDefinition<T>` (sealed: Static, Dynamic, Sequence, Association)

See `docs/implementation/phase2-core.md`

### Phase 3: Essentials (2 weeks)
- SequenceManager with AtomicInteger for thread-safety
- AssociationResolver with circular reference detection
- Transient attributes and TransientContext
- Integration tests (PostgreSQL, MySQL, H2)

See `docs/implementation/phase3-essentials.md`

### Phases 4-8
See `docs/implementation/` for Phase 4 (Extensions), Phase 5 (Performance), Phase 6 (Transactions), Phase 7 (Quality), Phase 8 (Release)

## Module Structure

Planned package organization:
```
com.example.faktory/
├── core/           # FactoryDefinition, AttributeDefinition
├── dsl/            # FactoryDslBuilder, DSL functions
├── builder/        # FactoryBuilder implementations
├── registry/       # FactoryRegistry, GlobalFactoryRegistry
├── sequence/       # SequenceManager
├── association/    # AssociationResolver, CircularDependencyDetector
├── callback/       # CallbackRegistry
├── trait/          # TraitDefinition, TraitApplicator
├── transaction/    # TransactionManager
├── jooq/           # JooqTableResolver, JooqRecordBuilder, ForeignKeyResolver
└── batch/          # BatchBuilder
```

## Key Design Patterns

### Factory DSL Example
```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25

    trait("admin") {
        role = UserRole.ADMIN
    }

    afterCreate { user ->
        AuditLogFactory.create(targetId = user.id)
    }
}
```

### Build Strategies
- `build()`: Object creation only (no DB)
- `create()`: DB persistence with beforeCreate/afterCreate callbacks
- `buildList(count)`: Multiple builds
- `createList(count)`: Batch INSERT optimization (20-30x faster)
- `attributes()`: Map of evaluated attributes (for API tests)

### Thread Safety
- SequenceManager: Uses `ConcurrentHashMap` + `AtomicInteger`
- FactoryRegistry: Immutable after registration (thread-safe reads)
- Global state: `ThreadLocal` for transaction context and circular dependency detection

### Error Handling
```kotlin
sealed class FactoryException : RuntimeException()
class FactoryNotFoundException(recordClass: KClass<*>, name: String?) : FactoryException()
class CircularAssociationException(chain: List<KClass<*>>) : FactoryException()
class InvalidAttribute(name: String, reason: String) : FactoryException()
```

## Testing Strategy

### Test Organization
- `src/test/kotlin/com/example/faktory/core/`: Unit tests
- `src/test/kotlin/com/example/faktory/integration/`: Integration tests
- Target coverage: 90%+

### Test Patterns
```kotlin
@TestInstance(Lifecycle.PER_METHOD)
class FactoryTest {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `factory creates record with default values`() {
        factory<UserRecord> {
            name = "User"
        }

        val user = UserFactory.build()

        assertThat(user.name).isEqualTo("User")
    }
}
```

### Integration Test Pattern
```kotlin
@SpringBootTest
@Testcontainers
class PostgreSQLIntegrationTest {
    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16")

    @Test
    fun `factory persists to database`() {
        val user = UserFactory.create()

        val found = userRepository.findById(user.id)
        assertThat(found).isNotNull()
    }
}
```

## Documentation Structure

All design documentation is in `docs/`:

- `planning/`: Product vision, backlog, sprint planning
- `architecture/`: System design, core interfaces, jOOQ integration
- `features/`: Detailed specs for each feature (10 docs)
- `implementation/`: Phase-by-phase implementation guides (8 docs)

**Start here**: `docs/README.md` for navigation

## Quality Gates (Phase 7)

Before release:
- ✅ Test coverage ≥ 90%
- ✅ All unit tests pass
- ✅ All integration tests pass (PostgreSQL, MySQL, H2)
- ✅ Performance benchmarks meet targets
- ✅ KDoc on all public APIs
- ✅ Zero P0 bugs
- ✅ ktlint + detekt pass

## Dependencies

### Runtime
- Kotlin stdlib + reflect
- jOOQ 3.18.7 (core + kotlin extensions)
- HikariCP (connection pooling)

### Test
- JUnit Jupiter 5.10.1
- AssertJ 3.24.2
- MockK 1.13.8
- Testcontainers (PostgreSQL, MySQL)

### Build
- Gradle Kotlin DSL
- ktlint plugin
- detekt plugin
- jacoco plugin
- jOOQ codegen plugin

## jOOQ Integration Notes

- Uses `DSLContext.newRecord()` for record creation
- Table/field resolution via reflection from jOOQ generated classes
- Foreign key information from jOOQ metadata (`Table.references`)
- Batch operations via `DSLContext.batchInsert()`
- Transactions via `DSLContext.transactionResult()`
- Field name mapping: camelCase (Kotlin) ↔ snake_case (DB)

## Common Pitfalls to Avoid

1. **Thread Safety**: Always use `AtomicInteger` for sequences, `ConcurrentHashMap` for registries
2. **Circular References**: Must detect in `AssociationResolver` using `ThreadLocal` stack
3. **Memory Leaks**: Clear `ThreadLocal` in finally blocks
4. **jOOQ Generated Code**: Don't edit - regenerate from schema
5. **Transaction Scope**: Use `withFactoryTransaction {}` to ensure proper cleanup
6. **Sequence Reset**: Call `GlobalSequenceManager.reset()` in `@BeforeEach`

## Next Steps

1. **Phase 1**: Initialize project structure following `docs/implementation/phase1-foundation.md`
2. **Phase 2**: Implement core registry and DSL following `docs/implementation/phase2-core.md`
3. Reference architecture docs in `docs/architecture/` for design decisions
4. Consult feature docs in `docs/features/` for detailed specifications
