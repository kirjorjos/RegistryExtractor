#!/usr/bin/env bash

set -euo pipefail

MINECHECK_MIN_VERSION="${MINECHECK_MIN_VERSION:-1.0.0}"
MINECHECK_REPO="${MINECHECK_REPO:-https://github.com/kirjorjos/Minecheck.git}"

LOADER="${LOADER:-forge}"
EXPECTED_VERSION="${EXPECTED_VERSION:-1.19.2}"
MINECHECK_TAG="$EXPECTED_VERSION/v$MINECHECK_MIN_VERSION"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TESTS_DIR="$ROOT/tests/minecheck"
SCRIPTS_DIR="$TESTS_DIR/scripts"

minecheck_version() {
    sed -n 's/^version *= *"\([^"]*\)".*/\1/p' "$1/build.gradle.kts" 2>/dev/null | head -1 || true
}

version_ge() {
    [ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | tail -1)" = "$1" ]
}

resolve_minecheck() {
    local candidates=()
    [ -n "${MINECHECK_DIR:-}" ] && candidates+=("$MINECHECK_DIR")
    candidates+=("$ROOT/../Minecheck" "$ROOT/.minecheck")

    for candidate in "${candidates[@]}"; do
        [ -d "$candidate/.git" ] || continue
        local version
        version="$(minecheck_version "$candidate")"
        if [ -z "$version" ]; then
            echo "warning: skipping $candidate — it declares no version" >&2
        elif version_ge "$version" "$MINECHECK_MIN_VERSION"; then
            echo "$candidate"
            return 0
        else
            echo "warning: skipping $candidate — it declares $version, below the $MINECHECK_MIN_VERSION floor" >&2
        fi
    done

    if [ -e "$ROOT/.minecheck" ]; then
        cat >&2 <<EOF
error: $ROOT/.minecheck is not Minecheck >= $MINECHECK_MIN_VERSION.

Remove it, or point MINECHECK_DIR at a checkout that declares >= $MINECHECK_MIN_VERSION.
EOF
        return 1
    fi

    echo "fetching Minecheck $MINECHECK_TAG from $MINECHECK_REPO" >&2
    if ! git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$MINECHECK_TAG" \
            "$MINECHECK_REPO" "$ROOT/.minecheck"; then
        cat >&2 <<EOF
error: could not fetch $MINECHECK_TAG from $MINECHECK_REPO.

Set MINECHECK_DIR to a checkout that declares >= $MINECHECK_MIN_VERSION.
EOF
        return 1
    fi
    echo "$ROOT/.minecheck"
}

is_jdk17() { [ -n "${1:-}" ] && [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q '"17\.'; }

if is_jdk17 "${JAVA_HOME:-}"; then
    :
elif is_jdk17 /usr/lib/jvm/java-17-openjdk-amd64; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    echo "using JDK 17 from $JAVA_HOME"
else
    echo "warning: no JDK 17 found — Minecheck's build expects one (its CI installs 17)" >&2
fi

MINECHECK="$(resolve_minecheck)"
RUN_DIR="$MINECHECK/src/$LOADER/mod/run/client"
MODS_DIR="$RUN_DIR/mods"
TASK="test${LOADER^}" # forge -> testForge

echo "Minecheck : $MINECHECK (declares $(minecheck_version "$MINECHECK"))"
RELEASE_COMMIT="$(git -C "$MINECHECK" rev-parse -q --verify "refs/tags/$MINECHECK_TAG^{commit}" || true)"
if [ -n "$RELEASE_COMMIT" ] && [ "$RELEASE_COMMIT" != "$(git -C "$MINECHECK" rev-parse HEAD)" ]; then
    echo "          : HEAD $(git -C "$MINECHECK" rev-parse --short HEAD) is past the $MINECHECK_TAG release"
fi
echo "task      : $TASK   run dir: $RUN_DIR"

# ---------------------------------------------------------------- 1. build the extractor
# unimined lays the loader out as `src/<loader>/mod`, the same path the shared CI assumes.
MODULE_DIR="$ROOT/src/$LOADER/mod"
if [ ! -d "$MODULE_DIR" ]; then
    echo "error: this checkout has no $LOADER module ($MODULE_DIR)" >&2
    exit 1
fi
BUILD_TASKS=":${LOADER}-mod:build"

# A caller that already built the jar (a Gradle task that owns the build) passes it in: a nested
# `./gradlew` against the same project would contend for the locks of the invocation that started it.
EXTRACTOR_JAR="${EXTRACTOR_JAR:-}"
if [ -z "$EXTRACTOR_JAR" ]; then
    echo
    echo "== building the extractor jar ($BUILD_TASKS)"
    (cd "$ROOT" && ./gradlew $BUILD_TASKS --console=plain -q)
    EXTRACTOR_JAR="$(ls -1t "$MODULE_DIR"/build/libs/RegistryExtractor-*-deobf.jar 2>/dev/null | head -1 || true)"
fi
if [ -z "$EXTRACTOR_JAR" ] || [ ! -f "$EXTRACTOR_JAR" ]; then
    echo "error: no deobf extractor jar in $MODULE_DIR/build/libs — run ./gradlew $BUILD_TASKS" >&2
    exit 1
fi

# `productionJar` carries Forge's `m_`/`f_` names; the development client needs the deobf jar.
if unzip -p "$EXTRACTOR_JAR" '*.class' 2>/dev/null | strings | grep -qE '^f_[0-9]+_$'; then
    echo "error: $EXTRACTOR_JAR is SRG-reobfuscated; this client needs the deobf jar" >&2
    exit 1
fi

mkdir -p "$MODS_DIR"
rm -f "$MODS_DIR"/RegistryExtractor-*.jar "$MODS_DIR"/registry-extractor-*.jar
cp "$EXTRACTOR_JAR" "$MODS_DIR/"
echo "deployed  : $(basename "$EXTRACTOR_JAR") -> $MODS_DIR"

rm -f "$RUN_DIR/extractor-test.json" "$RUN_DIR/extractor-typed.json" "$RUN_DIR/registry.json" \
    "$RUN_DIR/config/minecheck/run-result.json"

echo
echo "== running $TASK (headless; the task wraps the client in xvfb)"
echo "   the JSON assertions are a Gradle finalizer of $TASK: they run once the client has"
echo "   exited, and they still run when the in-game expects fail (a finalizer ignores the outcome)"
status=0
(cd "$MINECHECK" && ./gradlew -I "$TESTS_DIR/extractor-tests.init.gradle" "$TASK" \
    "-Pminecheck.extractor.task=$TASK" \
    "-Pminecheck.extractor.scriptsDir=$SCRIPTS_DIR" \
    "-Pminecheck.extractor.runDir=$RUN_DIR" \
    "-Pminecheck.extractor.script=extractor-client.lua" \
    "-Pminecheck.extractor.validatorScript=$TESTS_DIR/validate-output.gradle" \
    "-Pminecheck.extractor.expectedVersion=$EXPECTED_VERSION" \
    --rerun --no-daemon --console=plain) || status=$?

# The shared lifecycle workflow uploads `src/<loader>/mod/run` as this leg's artifact, but the run
# happens inside the Minecheck checkout. Copy what a failure is diagnosed from there - before the
# status decides the exit code, so a red run still leaves its log and JSON behind.
REPORT_DIR="${REPORT_DIR:-}"
if [ -n "$REPORT_DIR" ]; then
    [[ "$REPORT_DIR" = /* ]] || REPORT_DIR="$ROOT/$REPORT_DIR"
    rm -rf "$REPORT_DIR"
    mkdir -p "$REPORT_DIR/client/config/minecheck" "$REPORT_DIR/client/logs"
    cp "$RUN_DIR"/extractor-*.json "$REPORT_DIR/client/" 2>/dev/null || true
    cp "$RUN_DIR"/registry.json "$REPORT_DIR/client/" 2>/dev/null || true
    cp "$RUN_DIR"/config/minecheck/run-result.json "$REPORT_DIR/client/config/minecheck/" 2>/dev/null || true
    cp "$RUN_DIR"/logs/latest.log "$REPORT_DIR/client/logs/" 2>/dev/null || true
    echo "reports   : copied to $REPORT_DIR/client"
fi

exit "$status"
