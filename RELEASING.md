# Release Instructions

Publishing uses Vanniktech's Maven Central Portal support. Do not use OSSRH,
S01 URLs, or the generic `publish` task for Central releases.

## Prerequisites

- Central Portal namespace `io.github.fluxo-kt` is verified.
- GitHub secrets exist:
  - `MAVEN_CENTRAL_USERNAME`
  - `MAVEN_CENTRAL_PASSWORD`
  - `SIGNING_IN_MEMORY_KEY`
  - `SIGNING_IN_MEMORY_KEY_PASSWORD`
  - `SIGNING_IN_MEMORY_KEY_ID` when the exported key needs an explicit key ID
- Release artifacts are uploaded with:
  `./gradlew publishToMavenCentral --no-configuration-cache`.
- Release publication is manual in Central Portal. The build must not call
  `publishAndReleaseToMavenCentral` or enable automatic publishing.

## Snapshot Flow

Snapshots are published from the default branch by the build workflow when the
project version ends with `-SNAPSHOT`.

Use the Central Portal snapshot repository when consuming snapshots:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}
```

## Release Flow

1. Ensure `gradle/libs.versions.toml` has the intended non-SNAPSHOT version.
2. Run `./gradlew --dependency-verification strict check --no-configuration-cache`.
3. Run `./gradlew publishToMavenLocal`.
4. Inspect generated POMs and artifacts under
   `~/.m2/repository/io/github/fluxo-kt/fluxo-io-rad*`.
5. Push a signed release tag `v<version>`.
6. Let `.github/workflows/release.yml` run `publishToMavenCentral`.
7. Review the deployment in Central Portal, then publish it manually.

## Verification

Before publishing a release deployment, verify:

- Root metadata coordinate is `io.github.fluxo-kt:fluxo-io-rad:<version>`.
- Target artifacts keep the `fluxo-io-rad-*` prefix.
- Each publication has a POM, Gradle module metadata, sources jar, and javadoc
  jar.
- POM metadata includes name, description, URL, inception year, Apache-2.0
  license, developer, and SCM fields.
- `compileOnly` dependencies do not become runtime dependencies.
- Non-SNAPSHOT artifacts are signed.

## Rollback

- Before manual Central Portal publication, drop the deployment in Central
  Portal and fix the source problem.
- After Central publication, do not overwrite the version. Create a corrected
  follow-up version and document the bad version in release notes.
