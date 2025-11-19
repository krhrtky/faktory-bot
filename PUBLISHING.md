# Publishing Guide

This guide explains how to publish the Faktory Bot library to GitHub Packages.

## Prerequisites

1. GitHub personal access token with `write:packages` permission
2. GitHub account with write access to the repository

## Publishing to GitHub Packages

### Option 1: Using Environment Variables (Recommended for CI/CD)

Set the following environment variables:
```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-github-token
```

Then run:
```bash
./gradlew publish
```

### Option 2: Using Gradle Properties

Create or edit `~/.gradle/gradle.properties`:
```properties
gpr.user=your-github-username
gpr.token=your-github-token
```

Then run:
```bash
./gradlew publish
```

### Option 3: Command Line Properties

```bash
./gradlew publish -Pgpr.user=your-github-username -Pgpr.token=your-github-token
```

## What Gets Published

The following artifacts will be published to GitHub Packages:

1. **faktory-core-0.1.0.jar** - Main library JAR
2. **faktory-core-0.1.0-sources.jar** - Source code JAR
3. **faktory-core-0.1.0-javadoc.jar** - Javadoc JAR
4. **faktory-core-0.1.0.pom** - Maven POM file with dependencies

Published to: `https://maven.pkg.github.com/krhrtky/faktory-bot`

## Using the Published Library

To use the published library in another project, add the following to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/krhrtky/faktory-bot")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
        }
    }
}

dependencies {
    implementation("com.example:faktory-core:0.1.0")
}
```

## Verify Publication

After publishing, verify the package at:
https://github.com/krhrtky/faktory-bot/packages

## Troubleshooting

### Authentication Errors

If you get a 401 Unauthorized error:
1. Verify your GitHub token has `write:packages` permission
2. Check that GITHUB_ACTOR and GITHUB_TOKEN are set correctly
3. Ensure the token hasn't expired

### Network Errors

If you get network/connection errors:
1. Check your internet connection
2. Verify GitHub Packages is accessible: `curl https://maven.pkg.github.com`
3. Try again with `--stacktrace` for more details: `./gradlew publish --stacktrace`

## Publishing Tasks

- `./gradlew publishToMavenLocal` - Publish to local Maven repository (~/.m2/repository)
- `./gradlew publish` - Publish to GitHub Packages
- `./gradlew publishMavenPublicationToGitHubPackagesRepository` - Explicit publish to GitHub Packages
