# Release Instructions

Publishing uses Vanniktech's Maven Central Portal support. Do not use retired
Sonatype snapshot URLs or the generic `publish` task for Central releases.

## Prerequisites

- Central Portal namespace `io.github.fluxo-kt` is verified.
- Tag ruleset matching `refs/tags/v*` is active on the repo. `release.yml`
  hard-gates on `github.ref_protected`; without a matching ruleset the
  workflow exits before signing/publish (security boundary: an attacker
  with write access must not be able to publish via an unprotected tag).
  Create once with:
  `gh api -X POST repos/fluxo-kt/fluxo-io/rulesets --input ruleset.json`
  where `ruleset.json` defines `target=tag`, `enforcement=active`,
  `conditions.ref_name.include=["refs/tags/v*"]`, and at least one rule
  (deletion-block, non-fast-forward-block, required-signatures).
- GitHub secrets exist (org-level, all set up):
  - `MAVEN_CENTRAL_USERNAME`
  - `MAVEN_CENTRAL_PASSWORD`
  - `SIGNING_KEY` — ASCII-armored PGP private key (`gpg --armor --export-secret-keys <key-id>`)
  - `SIGNING_PASSWORD` — passphrase for the signing key
- Vanniktech's `signingInMemoryKeyId` is intentionally NOT wired: the plugin
  auto-derives the subkey from the imported in-memory key body. If `SIGNING_KEY`
  carries multiple signing subkeys and Vanniktech picks the wrong one, the fix
  is to add `signingInMemoryKeyId` env+secret in the publish steps — not the
  default.
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
5. Finalize the `CHANGELOG.md` section `## [<version>]`; the GitHub release
   draft uses only that section as the release body.
6. Push a signed release tag `v<version>` that exactly matches the catalog
   version.
7. Let `.github/workflows/release.yml` run `publishToMavenCentral`.
8. Review the deployment in Central Portal, then publish it manually.
9. Confirm `https://repo1.maven.org/maven2/io/github/fluxo-kt/fluxo-io-rad/<version>/`
   exists before README or CHANGELOG present the version as released. A pushed
   tag proves nothing: if the release workflow fails, nothing is published.

## Verification

Before publishing a release deployment, verify:

- `scripts/consumer-check.sh` passes: a consumer on the oldest supported Kotlin
  resolves, compiles, links and runs the locally published build, and no
  published metadata names a Beta/RC dependency. CI does not run it.
- Root metadata coordinates are `io.github.fluxo-kt:fluxo-io-rad:<version>`,
  `fluxo-io-rad-okio:<version>`, `fluxo-io-rad-kotlinx-io:<version>` and
  `fluxo-io-bom:<version>` (a POM-only platform listing the other three).
- Target artifacts keep their module-name prefix.
- Each library publication has a POM, Gradle module metadata, sources jar, and
  javadoc jar; the BOM has the POM and module metadata only.
- POM metadata includes name, description, URL, inception year, Apache-2.0
  license, developer, and SCM fields.
- `compileOnly` dependencies do not become runtime dependencies.
- Non-SNAPSHOT artifacts are signed.
- Published artifacts have GitHub provenance attestations. Verify a downloaded
  artifact with:
  `gh attestation verify <artifact> --repo fluxo-kt/fluxo-io`.

## Rollback

- Before manual Central Portal publication, drop the deployment in Central
  Portal and fix the source problem.
- After Central publication, do not overwrite the version. Create a corrected
  follow-up version and document the bad version in release notes.
