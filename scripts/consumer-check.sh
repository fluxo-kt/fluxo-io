#!/bin/sh
# Proves the published consumer floor: a consumer on the oldest Kotlin able to read this build's
# klibs (the library compiler's major.minor.0; see compat/consumer/build.gradle.kts) resolves,
# compiles, links and runs fluxo-io-rad on every target it builds, and the published metadata
# names no pre-release (Beta/RC) dependency.
set -eu
cd "$(dirname "$0")/.."

catalog() { awk -F ' *= *' -v k="$1" '$1 == k { gsub(/"/, "", $2); print $2; exit }' gradle/libs.versions.toml; }
version="$(catalog version)"
kotlin="$(catalog kotlin)"
test -n "$version" && test -n "$kotlin"
# A pre-release compiler has no older release of its own minor, so it is its own floor.
case "$kotlin" in
  *-*) floor="$kotlin" ;;
  *) floor="${kotlin%.*}.0" ;;
esac

# An unsigned local build must never reach the real ~/.m2: any project on this machine that
# resolves mavenLocal() would take it instead of the published artifact. Gradle reads
# maven.repo.local for both publishing and mavenLocal() resolution.
local_m2="$PWD/build/consumer-check-m2"
./gradlew publishToMavenLocal -Pfluxo.unsignedLocalPublish=true --no-configuration-cache "-Dmaven.repo.local=$local_m2" "$@"

repo="$local_m2/io/github/fluxo-kt"
modules=$(find "$repo" -path "*/fluxo-io-rad*/$version/*.module")
test -n "$modules"
# A pre-release coordinate in published metadata would force consumers onto a Beta/RC
# toolchain library; Kotlin-built klibs otherwise stay consumable by the floor compiler.
# Dependency versions are rich objects ("version": {"requires": "…"}), so every key is matched.
if grep -E -n '"(version|requires|strictly|prefers)": *"[^"]*-(Beta|RC|dev|M)[0-9]*"' $modules; then
  echo "consumer-check: published metadata references a pre-release dependency" >&2
  exit 1
fi

# The library changes under one version string, and the consumer's Kotlin incremental caches do
# not survive that (Kotlin/Wasm linking failed with an internal compiler error), so start clean.
rm -rf compat/consumer/build compat/consumer/.kotlin
# The yarn lock store is bookkeeping for a committed lock file; this throwaway consumer has none.
./gradlew -p compat/consumer check -x kotlinStoreYarnLock "-Dmaven.repo.local=$local_m2" -PfluxoIoVersion="$version" -PconsumerKotlin="$floor"
