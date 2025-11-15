# Faktory Bot Usage Guide

This directory contains comprehensive usage documentation for Faktory Bot.

## Table of Contents

1. [Getting Started](01-getting-started.md) - Installation and basic setup
2. [Basic Usage](02-basic-usage.md) - build(), create(), buildList(), createList()
3. [Sequences](03-sequences.md) - Generating unique values
4. [Traits](04-traits.md) - Defining attribute variations
5. [Callbacks](05-callbacks.md) - Lifecycle hooks (afterBuild, beforeCreate, afterCreate)
6. [Transients](06-transients.md) - Temporary attributes for callbacks
7. [Transactions](07-transactions.md) - Transaction management and rollback
8. [Advanced Topics](08-advanced.md) - Complex scenarios and best practices

## Quick Reference

### Define a Factory

```kotlin
factory<UserRecord> {
    name = "Default User"
    email = sequence { n -> "user${n}@example.com" }
    age = 25
}
```

### Build and Create Records

```kotlin
val user = dsl.factory<UserRecord>().build()           // In-memory only
val user = dsl.factory<UserRecord>().create()          // Persisted to DB
val users = dsl.factory<UserRecord>().buildList(10)    // 10 in-memory records
val users = dsl.factory<UserRecord>().createList(10)   // 10 persisted records
```

### Use Traits

```kotlin
factory<UserRecord> {
    name = "User"
    email = "user@example.com"
    age = 25

    trait("admin") {
        attribute("name", "Admin User")
        attribute("age", 35)
    }
}

val admin = dsl.factory<UserRecord>().build("admin")
```

### Override Attributes

```kotlin
val user = dsl.factory<UserRecord>().build(mapOf(
    "name" to "Custom Name",
    "age" to 30
))
```

## Examples

See the [examples/](../../examples/) directory for working code samples:

- `BasicExample.kt` - Basic factory usage
- `SequenceExample.kt` - Sequence generation
- `TraitExample.kt` - Trait variations
- `CallbackExample.kt` - Lifecycle callbacks
- `TransientExample.kt` - Transient attributes
- `AdvancedExample.kt` - Complex scenarios

## Documentation Organization

Each document follows this structure:

1. **Overview** - What the feature does
2. **Basic Usage** - Simple examples
3. **Advanced Usage** - Complex scenarios
4. **API Reference** - Method signatures
5. **Best Practices** - Recommended patterns
6. **Common Pitfalls** - What to avoid

## Getting Help

- See [Getting Started](01-getting-started.md) for initial setup
- Check [Advanced Topics](08-advanced.md) for complex scenarios
- Review [examples/](../../examples/) for working code
- Consult API docs in `docs/architecture/` for design details
