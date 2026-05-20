# Security Policy

## Supported Versions

`fluxo-io` is alpha software. Security fixes are made on the default branch
and released in the next available version. Consumers should upgrade to the
latest release or snapshot that contains the fix.

## Reporting

Report suspected vulnerabilities with GitHub Security Advisories:

<https://github.com/fluxo-kt/fluxo-io/security/advisories/new>

If GitHub advisories are unavailable, contact the maintainer listed in the
published POM metadata. Do not open a public issue for an undisclosed
vulnerability.

## Dependency Integrity

The build uses Gradle dependency verification in strict mode. Dependency or
plugin updates must refresh `gradle/verification-metadata.xml` through
`./updateBaseline` and must not add unreviewed repositories.

## Provenance

Release and default-branch publication workflows create GitHub artifact
attestations before `publishToMavenCentral`. Verify a downloaded artifact with:

```sh
gh attestation verify <artifact> --repo fluxo-kt/fluxo-io
```
