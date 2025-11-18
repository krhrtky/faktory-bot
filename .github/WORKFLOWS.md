# GitHub Actions Workflows

This document describes the CI/CD workflows for the Faktory Bot project located in `.github/workflows/`.

## Workflows Overview

### 1. CI Workflow (`ci.yml`)

**Trigger**: Push and PR to `master`, `main`, `develop` branches

**Purpose**: Main continuous integration pipeline for code quality and unit testing

**Jobs**:
- **lint-and-format**: Runs ktlint and detekt for code style and static analysis
- **build-and-test**: Builds the project and runs unit tests
  - Verifies 90%+ code coverage threshold
  - Uploads test results and coverage reports
  - Uploads coverage to Codecov
- **quality-gate**: Ensures all quality checks pass

**Artifacts**:
- Test results
- Coverage reports (HTML and XML)

### 2. Integration Tests Workflow (`integration-tests.yml`)

**Trigger**: Push and PR to `master`, `main`, `develop` branches

**Purpose**: Run integration tests against multiple database engines

**Strategy**: Matrix testing with PostgreSQL and MySQL

**Jobs**:
- **integration-tests**: Runs integration tests for each database type in parallel
- **integration-summary**: Aggregates results from all database tests

**Note**: H2 database tests are included in the main CI unit tests (not integration tests) since H2 runs in-process and doesn't require Testcontainers.

**Environment Variables**:
- `DB_TYPE`: Database type (postgresql, mysql)
- `DB_HOST`: Database host (localhost for CI)
- `DB_PORT`: Database port
- `DB_NAME`: Database name (testdb)
- `DB_USER`: Database user
- `DB_PASSWORD`: Database password

### 3. Release Workflow (`release.yml`)

**Trigger**: Push tags matching `v*.*.*` (e.g., v0.1.0, v1.2.3)

**Purpose**: Build, publish, and release artifacts

**Jobs**:
- **build**:
  - Validates version matches tag
  - Runs quality checks
  - Builds release artifacts
  - Generates documentation
- **publish**: Publishes artifacts to GitHub Packages
- **create-release**: Creates GitHub release with changelog

**Prerequisites**:
- Tag version must match `version` in `build.gradle.kts`
- All quality gates must pass
- CHANGELOG.md should contain version notes

**Publishing**:
Artifacts are published to GitHub Packages and can be consumed via:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/OWNER/faktory-bot")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}
```

### 4. Dependency Review (`dependency-review.yml`)

**Trigger**: Pull requests only

**Purpose**: Security and license compliance for dependencies

**Jobs**:
- **dependency-review**: GitHub dependency review action
  - Fails on moderate or higher severity vulnerabilities
  - Denies GPL-2.0 and GPL-3.0 licenses
  - Posts summary in PR comments
- **gradle-dependency-check**: Checks for available dependency updates

**Security Levels**:
- Critical/High: Build fails
- Moderate: Build fails
- Low: Warning only

### 5. CodeQL Security Scan (`codeql.yml`)

**Trigger**:
- Push and PR to `master`, `main`, `develop` branches
- Weekly schedule (Mondays at 00:00 UTC)

**Purpose**: Advanced security vulnerability scanning

**Analysis**:
- Language: Java/Kotlin
- Query suites: `security-extended`, `security-and-quality`
- Reports to GitHub Security tab

### 6. PR Labeler (`labeler.yml`)

**Trigger**: PR opened, synchronized, or reopened

**Purpose**: Automatically label PRs based on changed files and size

**Labels Applied**:

**By File Path**:
- `documentation`: Changes to docs, markdown files
- `dependencies`: Changes to Gradle build files
- `ci/cd`: Changes to GitHub Actions workflows
- `core`: Changes to main source code
- `tests`: Changes to test files
- `examples`: Changes to example code
- `configuration`: Changes to config files

**By Size**:
- `size/xs`: 1-10 lines changed
- `size/s`: 11-50 lines changed
- `size/m`: 51-200 lines changed
- `size/l`: 201-500 lines changed
- `size/xl`: 500+ lines changed

## Workflow Dependencies

```
CI Workflow
├── lint-and-format (parallel)
├── build-and-test (depends on lint-and-format)
└── quality-gate (depends on all)

Integration Tests
├── integration-tests (matrix: postgresql, mysql)
└── integration-summary (depends on integration-tests)

Release Workflow
├── build (parallel)
├── publish (depends on build)
└── create-release (depends on build)
```

## Local Testing

### Test CI Workflow Locally

```bash
# Run linting
./gradlew ktlintCheck detekt

# Run unit tests with coverage
./gradlew test jacocoTestReport jacocoTestCoverageVerification

# Build
./gradlew build
```

### Test Integration Tests Locally

```bash
# Start databases with Docker Compose
docker-compose up -d

# Run integration tests
./gradlew test --tests '*IntegrationTest'

# Stop databases
docker-compose down
```

### Validate Release Locally

```bash
# Check version
./gradlew properties | grep "^version:"

# Build release artifacts
./gradlew clean build -x test

# Check artifacts
ls -la faktory-core/build/libs/
```

## Configuration Files

- `.github/labeler.yml`: PR labeler configuration
- `detekt.yml`: Static analysis rules (root directory)
- `build.gradle.kts`: Gradle build configuration

## Quality Gates

All PRs must pass:
- ✅ ktlint code formatting
- ✅ detekt static analysis
- ✅ 90%+ test coverage
- ✅ All unit tests (including H2)
- ✅ All integration tests (PostgreSQL, MySQL)
- ✅ No moderate+ severity vulnerabilities
- ✅ No GPL license dependencies

## Secrets Required

For full functionality, configure these secrets in repository settings:

- `GITHUB_TOKEN`: Automatically provided by GitHub Actions
- `CODECOV_TOKEN`: (Optional) For Codecov integration

## Caching Strategy

All workflows use Gradle build action caching:
- Cache is read-only for non-main branches (prevents cache pollution)
- Cache includes Gradle dependencies and build outputs
- Significantly speeds up builds (30-60% faster)

## Troubleshooting

### Build fails in CI but works locally

1. Check Testcontainers configuration - CI doesn't use local Docker sockets
2. Verify environment variables are not hardcoded
3. Check for filesystem path assumptions (different on Linux)

### Coverage threshold fails

```bash
# Check current coverage
./gradlew jacocoTestReport
open faktory-core/build/reports/jacoco/test/html/index.html
```

Coverage threshold is 90% - see `build.gradle.kts`:

```kotlin
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

### Integration tests timeout

- Increase health check retries in workflow
- Check database service configuration
- Verify Testcontainers is properly configured

## Monitoring

- Check workflow runs: https://github.com/OWNER/faktory-bot/actions
- View security alerts: https://github.com/OWNER/faktory-bot/security
- Check code coverage: https://codecov.io/gh/OWNER/faktory-bot

## Future Enhancements

Potential improvements:
- [ ] Performance benchmarking workflow
- [ ] Automated dependency updates (Dependabot/Renovate)
- [ ] Deploy documentation to GitHub Pages
- [ ] Publish to Maven Central
- [ ] Nightly builds for snapshot releases
- [ ] Multi-platform testing (different OS/JDK versions)
