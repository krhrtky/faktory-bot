# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Faktory Bot is a type-safe test data factory library for jOOQ and Kotlin, inspired by Ruby's Factory Bot. It provides a declarative DSL for defining and generating test data with full type safety and jOOQ integration.

**Status**: v0.2.0 - Core features implemented and tested.

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

## Development Philosophy

### t-wada TDD Principles (MUST FOLLOW)

**IMPORTANT**: All implementation MUST strictly follow Test-Driven Development as taught by t-wada (Takuto Wada).

#### The Three Laws of TDD
1. **Write no production code except to pass a failing test**
   - Never write production code without a failing test first
   - The test defines the specification

2. **Write only enough of a test to demonstrate a failure**
   - Write minimal test code to see it fail
   - One failing assertion at a time

3. **Write only enough production code to pass the currently failing test**
   - Implement the simplest solution that makes the test pass
   - No premature optimization or extra features

#### Red-Green-Refactor Cycle (MANDATORY)
1. **Red**: Write a failing test
   - Think about WHAT you want to achieve (specification)
   - Write the test as if the API already exists
   - Run the test and confirm it fails for the right reason

2. **Green**: Make it pass with minimum code
   - Write the simplest code that makes the test pass
   - Duplication is acceptable at this stage
   - Focus on making it work, not making it perfect

3. **Refactor**: Improve the code while keeping tests green
   - Remove duplication
   - Improve names and structure
   - Ensure all tests remain green throughout
   - Refactor both production code AND test code

#### TDD Workflow for This Project
```kotlin
// 1. RED: Write failing test first
@Test
fun `generate factory with required fields`() {
    val processor = FactoryGeneratorProcessor(mockCodeGen, mockLogger)
    val result = processor.process(mockResolver)

    // This will fail - implementation doesn't exist yet
    assertThat(result).isEmpty()
}

// 2. GREEN: Minimum implementation
override fun process(resolver: Resolver): List<KSAnnotated> {
    return emptyList() // Simplest code to pass
}

// 3. REFACTOR: Improve while keeping tests green
override fun process(resolver: Resolver): List<KSAnnotated> {
    val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_NAME)
    return symbols.filterNot { it.validate() }.toList()
}
```

#### TDD Discipline Rules
- ❌ **NEVER** write production code without a failing test
- ❌ **NEVER** write more test code than needed to fail
- ❌ **NEVER** write more production code than needed to pass
- ✅ **ALWAYS** see the test fail before making it pass
- ✅ **ALWAYS** keep the cycle short (minutes, not hours)
- ✅ **ALWAYS** refactor only when tests are green
- ✅ **ALWAYS** run all tests before committing

#### Benefits of TDD in This Project
1. **Specification**: Tests document how the code should behave
2. **Safety**: Refactoring is safe with comprehensive test coverage
3. **Design**: TDD drives better API design (test-first thinking)
4. **Confidence**: Every feature is verified by tests
5. **Debugging**: When a test fails, you know exactly what broke

**Remember**: If you're writing code without a test, STOP and write the test first.

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
./gradlew test --tests io.github.krhrtky.faktory.core.*

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

## Implemented Features (v0.2.0)

All core features have been implemented and tested:

### Core Features
- ✅ Factory DSL with type-safe attribute definitions
- ✅ Build strategies: `build()`, `create()`, `buildList()`, `createList()`
- ✅ Sequence generation with thread-safe counters
- ✅ Association resolution with circular dependency detection
- ✅ Transient attributes for callback parameters
- ✅ Trait system for reusable attribute sets
- ✅ Callback hooks: `afterBuild`, `beforeCreate`, `afterCreate`
- ✅ Transaction management with automatic rollback
- ✅ Factory registry with inheritance support
- ✅ Global trait system for cross-factory traits

### Project Structure
```
faktory-core/src/main/kotlin/io/github/krhrtky/faktory/
├── annotation/     # @FactoryGenerate (deprecated), @FactoryGenerated
├── core/           # FactoryDefinition, AttributeDefinition, CallbackRegistry, exceptions
├── dsl/            # FactoryDslBuilder, DSL functions
├── builder/        # FactoryBuilder implementations
├── registry/       # FactoryRegistry, GlobalFactoryRegistry, GlobalTraitRegistry
├── sequence/       # SequenceManager, GlobalSequenceManager
├── association/    # AssociationResolver, CircularDependencyDetector
├── trait/          # TraitApplicator
├── transaction/    # TransactionManager, JooqTransactionManager, deadlock retry
├── jooq/           # JooqTableResolver, RequiredAttributeValidator
└── lint/           # FactoryLinter
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
- `createList(count)`: Multiple creates (simple loop - batch optimization planned)
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
- `src/test/kotlin/io/github/krhrtky/faktory/*`: Unit tests (by package)
- `src/test/kotlin/io/github/krhrtky/faktory/integration/`: Integration tests
- Target coverage: 90%+ (currently configured minimum: 65%)

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
- Transactions via `DSLContext.transactionResult()` with savepoint support
- Field name mapping: camelCase (Kotlin) ↔ snake_case (DB)
- **Batch operations**: Planned feature using `DSLContext.batchInsert()` (not yet implemented)

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
