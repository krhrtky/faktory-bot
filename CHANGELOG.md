# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2025-01-15

### Added
- Factory definition DSL with type-safe attribute definitions
- Build strategies: `build()`, `create()`, `buildList()`, `createList()`
- Sequence generation with `sequence { n -> ... }`
- Association resolution with automatic related record creation
- Transient attributes for controlling callback behavior
- Trait system for reusable attribute sets
- Callback hooks: `afterBuild`, `beforeCreate`, `afterCreate`
- Transaction management with automatic rollback support
- JooqTransactionManager with nested transaction support via savepoints
- DeadlockRetryTransactionManager with exponential backoff
- JUnit5 integration via FactoryTransactionExtension
- Factory registry with parent/child inheritance
- Global sequence manager with thread-safe counters

### Changed
- N/A (initial release)

### Deprecated
- N/A

### Removed
- N/A

### Fixed
- N/A

### Security
- N/A

## [0.0.1] - Planning Phase
- Initial project structure and documentation
