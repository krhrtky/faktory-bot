# Release Workflow Verification

This document verifies the compatibility between the Maven publishing configuration and the GitHub Actions release workflow.

## ✅ Verification Summary

**Status**: ✅ **READY TO PUBLISH**

The release workflow (`.github/workflows/release.yml`) is fully compatible with the Maven publishing configuration in `faktory-core/build.gradle.kts`.

## Configuration Compatibility

### ✅ Environment Variables
- **GITHUB_TOKEN**: Automatically provided by GitHub Actions (line 85)
- **GITHUB_ACTOR**: Automatically provided by GitHub Actions (line 86)
- Both variables are correctly passed to `./gradlew publish` command

### ✅ Permissions
```yaml
permissions:
  contents: write   # For creating GitHub Releases
  packages: write   # For publishing to GitHub Packages
```
The workflow has the necessary permissions to publish packages.

### ✅ Authentication
The `faktory-core/build.gradle.kts` publishing configuration expects:
```kotlin
username = System.getenv("GITHUB_ACTOR")
password = System.getenv("GITHUB_TOKEN")
```
Both are provided by the workflow ✅

### ✅ Java Version
- Workflow uses: **Java 17** (Temurin distribution)
- Project requires: **Java 17** (configured in `faktory-core/build.gradle.kts`)
- Match: ✅

### ✅ Gradle Version
- Project uses: Gradle wrapper with version 8.5
- Workflow uses: `gradle/gradle-build-action@v2` with wrapper
- Match: ✅

## Workflow Overview

The release workflow consists of 3 parallel jobs:

### 1. Build Job
```
✓ Checkout code
✓ Set up Java 17
✓ Validate version (tag vs build.gradle.kts)
✓ Run quality checks (ktlint, detekt, tests, coverage)
✓ Build artifacts
✓ Upload artifacts
```

### 2. Publish Job
```
✓ Checkout code
✓ Set up Java 17
✓ Run: ./gradlew publish
  → Publishes to https://maven.pkg.github.com/krhrtky/faktory-bot
  → Artifacts:
     - faktory-core-0.1.0.jar
     - faktory-core-0.1.0-sources.jar
     - faktory-core-0.1.0-javadoc.jar
     - faktory-core-0.1.0.pom
```

### 3. Create Release Job
```
✓ Checkout code
✓ Download build artifacts
✓ Generate changelog
✓ Create GitHub Release with installation instructions
```

## Fixes Applied

### 1. Removed Dokka Documentation Build
**Before**:
```yaml
- name: Build documentation
  run: ./gradlew dokkaHtml
  continue-on-error: true
```

**After**: Removed (dokka plugin not configured in project)

**Reason**: Avoid unnecessary step and potential confusion

### 2. Removed `continue-on-error` from Publish
**Before**:
```yaml
- name: Publish to GitHub Packages
  run: ./gradlew publish
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    GITHUB_ACTOR: ${{ github.actor }}
  continue-on-error: true  # ❌ Hides failures
```

**After**:
```yaml
- name: Publish to GitHub Packages
  run: ./gradlew publish
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    GITHUB_ACTOR: ${{ github.actor }}
  # ✅ Fails explicitly if publish fails
```

**Reason**: Publishing failures should be explicit, not hidden

### 3. Updated Artifact Paths
**Before**:
```yaml
path: |
  **/build/libs/*.jar
  !**/*-javadoc.jar
  !**/*-sources.jar
```

**After**:
```yaml
path: |
  faktory-core/build/libs/*.jar
  !faktory-core/build/libs/*-javadoc.jar
  !faktory-core/build/libs/*-sources.jar
```

**Reason**: Explicitly target faktory-core module (more precise)

### 4. Updated Installation Instructions
**Before**: Used incorrect artifact ID `faktory-bot`

**After**: Uses correct artifact ID `faktory-core` with GitHub Packages authentication

**Reason**: Accurate installation instructions for users

## How to Trigger a Release

### Step 1: Ensure Version is Correct
Check `build.gradle.kts`:
```kotlin
version = "0.1.0"  // ✅ Should not contain -SNAPSHOT for release
```

### Step 2: Create and Push a Git Tag
```bash
# Tag format: v<major>.<minor>.<patch>
git tag v0.1.0

# Push the tag to trigger the workflow
git push origin v0.1.0
```

### Step 3: Monitor Workflow
1. Go to: https://github.com/krhrtky/faktory-bot/actions
2. Find the "Release" workflow run for tag `v0.1.0`
3. Monitor the progress of all 3 jobs:
   - ✅ Build Release Artifacts
   - ✅ Publish to GitHub Packages
   - ✅ Create GitHub Release

### Step 4: Verify Publication
After successful workflow run:

1. **Check GitHub Packages**:
   - https://github.com/krhrtky/faktory-bot/packages

2. **Check GitHub Release**:
   - https://github.com/krhrtky/faktory-bot/releases/tag/v0.1.0

## Testing the Workflow (Dry Run)

To test without creating a real release:

### Option 1: Manual Workflow Dispatch (if enabled)
Add to `.github/workflows/release.yml`:
```yaml
on:
  push:
    tags:
      - 'v*.*.*'
  workflow_dispatch:  # Manual trigger for testing
```

### Option 2: Test Tag
```bash
# Create a test tag
git tag v0.1.0-test
git push origin v0.1.0-test

# Delete after testing
git tag -d v0.1.0-test
git push origin :refs/tags/v0.1.0-test
```

### Option 3: Local Publishing Test
```bash
# Test publishing locally
export GITHUB_ACTOR=your-username
export GITHUB_TOKEN=your-token

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Check: ~/.m2/repository/com/example/faktory-core/0.1.0/
ls -la ~/.m2/repository/com/example/faktory-core/0.1.0/

# Expected files:
# - faktory-core-0.1.0.jar
# - faktory-core-0.1.0-sources.jar
# - faktory-core-0.1.0-javadoc.jar
# - faktory-core-0.1.0.pom
```

## Quality Gates

Before the workflow publishes, it verifies:

1. ✅ **Version Match**: Git tag version matches `build.gradle.kts` version
2. ✅ **Code Quality**: ktlint and detekt pass
3. ✅ **Tests**: All unit tests pass
4. ✅ **Coverage**: Code coverage meets 65% threshold
5. ✅ **Build**: Artifacts build successfully

If any gate fails, the workflow stops and does not publish.

## Expected Workflow Output

### Successful Run
```
✅ Build Release Artifacts (3m 45s)
   ✓ Validate version: 0.1.0 matches v0.1.0
   ✓ Quality checks passed
   ✓ Build successful
   ✓ Artifacts uploaded

✅ Publish to GitHub Packages (1m 30s)
   ✓ Publishing to https://maven.pkg.github.com/krhrtky/faktory-bot
   ✓ Published com.example:faktory-core:0.1.0

✅ Create GitHub Release (45s)
   ✓ Release v0.1.0 created
   ✓ Changelog generated
   ✓ Artifacts attached
```

### Failed Run (Example)
```
❌ Build Release Artifacts (2m 15s)
   ✓ Validate version: OK
   ✗ Quality checks: ktlint failed (3 formatting issues)

⏭️ Publish to GitHub Packages (skipped)
⏭️ Create GitHub Release (skipped)
```

## Troubleshooting

### Issue: Version Mismatch Error
```
❌ Version mismatch! Tag: 0.1.0, Gradle: 0.1.0-SNAPSHOT
```

**Solution**: Update `build.gradle.kts` to remove `-SNAPSHOT`:
```kotlin
version = "0.1.0"  // Not "0.1.0-SNAPSHOT"
```

### Issue: Publish Failed with 401 Unauthorized
```
❌ Could not PUT 'https://maven.pkg.github.com/...'. Received status code 401
```

**Solution**: Check workflow permissions in repository settings:
- Settings → Actions → General → Workflow permissions
- Select: "Read and write permissions"

### Issue: Publish Failed with 403 Forbidden
```
❌ Could not PUT 'https://maven.pkg.github.com/...'. Received status code 403
```

**Solution**: Verify `packages: write` permission in workflow file (already configured ✅)

### Issue: Quality Checks Failed
```
❌ ktlint check failed
```

**Solution**: Run locally and fix:
```bash
./gradlew ktlintFormat  # Auto-fix formatting
./gradlew ktlintCheck   # Verify
./gradlew detekt        # Check static analysis
```

## Next Steps After Verification

1. ✅ All configurations verified and compatible
2. ✅ Workflow updated and optimized
3. 🎯 **Ready to release**: Create tag `v0.1.0` to trigger publication

To publish version 0.1.0:
```bash
git tag v0.1.0
git push origin v0.1.0
```

## References

- GitHub Packages Documentation: https://docs.github.com/en/packages
- Gradle Publishing Plugin: https://docs.gradle.org/current/userguide/publishing_maven.html
- Release Workflow: `.github/workflows/release.yml`
- Publishing Configuration: `faktory-core/build.gradle.kts`
- Publishing Guide: `PUBLISHING.md`
