#!/usr/bin/env bash

set -euo pipefail

MINECHECK_MIN_VERSION="${MINECHECK_MIN_VERSION:-1.0.0}"
MINECHECK_REPO="${MINECHECK_REPO:-https://github.com/kirjorjos/Minecheck.git}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TESTS_DIR="$ROOT/tests/minecheck"
SCRIPTS_DIR="$TESTS_DIR/scripts"

LOADER="${LOADER:-forge}"

# `SCRIPT` picks which staged Lua file the run executes: the suite's client script, or the manual
# extraction's. `OUTPUT_FILE` is the name that extraction writes, substituted when scripts are staged.
SCRIPT="${SCRIPT:-extractor-client.lua}"
OUTPUT_FILE="${OUTPUT_FILE:-extractor-registry.json}"
case "$OUTPUT_FILE" in
    */*) echo "error: OUTPUT_FILE must be a bare file name, not a path: $OUTPUT_FILE" >&2; exit 1 ;;
esac

# Locales this run resolves. Empty keeps today's single extraction; `all` is the eleven the docs
# site carries; anything else is a comma-separated list of docs codes. The extractor is handed
# explicit codes through the run dir, so a typo fails here rather than producing untranslated files.
DOCS_LOCALES="cs_CZ de_DE en_US fr_FR ja_JP ko_KR nl_NL pt_BR ru_RU sv_SE zh_CN"
LOCALES="$(printf '%s' "${LOCALES:-}" | tr ',' ' ' | tr -s '[:space:]' ' ' | sed 's/^ //; s/ $//')"
if [ "$LOCALES" = "all" ]; then
    LOCALES="$DOCS_LOCALES"
fi
for code in $LOCALES; do
    case " $DOCS_LOCALES " in
        *" $code "*) : ;;
        *) echo "error: $code is not a docs locale: $DOCS_LOCALES (or all)" >&2; exit 1 ;;
    esac
done
# The locale list belongs to a manual extraction: the suite asserts its own four outputs, which a
# locale run would rename.
if [ "$SCRIPT" = "extractor-client.lua" ] && [ -n "$LOCALES" ]; then
    echo "error: LOCALES is for an extraction run, not the suite's client script" >&2
    exit 1
fi
# The file the extractor reads from its working directory; the name is LocaleExtraction.REQUEST_FILE.
REQUEST_FILE_NAME="extractor-locales.txt"

# Jars to load beside the extractor: the mod set a manual extraction asked for, from fetch-mods.sh.
MODS_EXTRA_DIR="${MODS_EXTRA_DIR:-}"

# The version comes from gradle.properties, the same file the build derives its mods.toml range from,
# so a version branch cannot run the suite against another branch's Minecraft.
BRANCH_VERSION="$(sed -n 's/^minecraft_version *= *\([^[:space:]]*\).*/\1/p' "$ROOT/gradle.properties" | head -1)"
EXPECTED_VERSION="${EXPECTED_VERSION:-$BRANCH_VERSION}"
if [ -z "$EXPECTED_VERSION" ]; then
    echo "error: gradle.properties declares no minecraft_version, so the suite cannot know which Minecraft it tests" >&2
    exit 1
fi
MINECHECK_TAG="$EXPECTED_VERSION/v$MINECHECK_MIN_VERSION"

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

# The harness's mods/ folder is shared between runs, so the mod set a previous manual extraction
# deployed is removed first: a run asking for fewer mods must not silently keep the ones it dropped.
MOD_SET_MANIFEST="$MODS_DIR/.extractor-mod-set.txt"
if [ -f "$MOD_SET_MANIFEST" ]; then
    while read -r deployed; do
        [ -n "$deployed" ] && rm -f "$MODS_DIR/$deployed"
    done < "$MOD_SET_MANIFEST"
    rm -f "$MOD_SET_MANIFEST"
fi
if [ -n "$MODS_EXTRA_DIR" ]; then
    if [ ! -d "$MODS_EXTRA_DIR" ]; then
        echo "error: MODS_EXTRA_DIR=$MODS_EXTRA_DIR is not a directory" >&2
        exit 1
    fi
    (cd "$MODS_EXTRA_DIR" && ls -1 ./*.jar 2>/dev/null | sed "s#^\./##") > "$MOD_SET_MANIFEST" || true
    extras="$(wc -l < "$MOD_SET_MANIFEST")"
    if [ "$extras" -gt 0 ]; then
        cp "$MODS_EXTRA_DIR"/*.jar "$MODS_DIR/"
    fi
    echo "mod set   : $extras jars from $MODS_EXTRA_DIR"
fi

# Deobf jars expect their development-only libraries beside them rather than inside themselves, so
# they belong on the client classpath; a library jar dropped into mods/ carries no mod metadata and
# Forge would skip it. fetch-mods.sh writes the coordinates down with the jars it fetched.
RUNTIME_LIBS=""
if [ -n "$MODS_EXTRA_DIR" ] && [ -s "$MODS_EXTRA_DIR/libs.txt" ]; then
    RUNTIME_LIBS="$(paste -sd, "$MODS_EXTRA_DIR/libs.txt")"
fi

# Everything this run is expected to write is deleted first, so what the validator sees afterwards was
# written by *this* run and not left over from an earlier one.
rm -f "$RUN_DIR/extractor-test.json" "$RUN_DIR/extractor-typed.json" "$RUN_DIR/registry.json" \
    "$RUN_DIR/extractor-spaced file.json" "$RUN_DIR/config/minecheck/run-result.json"
rm -f "$RUN_DIR"/extractor-registry-*.json
rm -f "$HOME/.minecheck-extractor-home.json"

# A previous dispatch's locale list must not leak into this run: the request file is rewritten (or
# removed) every time, so a run without locales cannot inherit the last one's.
rm -f "$RUN_DIR/$REQUEST_FILE_NAME"
if [ -n "$LOCALES" ]; then
    printf '%s\n' $LOCALES > "$RUN_DIR/$REQUEST_FILE_NAME"
    echo "locales   : $LOCALES"
fi

# Minecheck copies every file of the scripts directory into the run, so the suite is staged from
# templates: `__EXPECTED_VERSION__` is substituted here. That keeps the Lua version-agnostic without
# teaching the harness a variable syntax it does not have.
STAGE_DIR="$ROOT/build/minecheck-scripts"
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"
for script in "$SCRIPTS_DIR"/*.lua; do
    sed -e "s/__EXPECTED_VERSION__/$EXPECTED_VERSION/g" \
        -e "s#__OUTPUT_FILE__#$OUTPUT_FILE#g" \
        "$script" > "$STAGE_DIR/$(basename "$script")"
done

echo
echo "== running $TASK (headless; the task wraps the client in xvfb)"
if [ "$SCRIPT" = "extractor-client.lua" ]; then
    echo "   the JSON assertions are a finalizer of testForgeAll in this repo's build: they run once the"
    echo "   client has exited, and they still run when the in-game expects fail (a finalizer ignores the outcome)"
fi
status=0
(cd "$MINECHECK" && ./gradlew -I "$TESTS_DIR/extractor-tests.init.gradle" "$TASK" \
    "-Pminecheck.extractor.task=$TASK" \
    "-Pminecheck.extractor.scriptsDir=$STAGE_DIR" \
    "-Pminecheck.extractor.runDir=$RUN_DIR" \
    "-Pminecheck.extractor.script=$SCRIPT" \
    "-Pminecheck.extractor.runtimeLibs=$RUNTIME_LIBS" \
    --rerun --no-daemon --console=plain) || status=$?

# The name the extractor gives one locale's file: the output name with the code before its extension.
locale_output() {
    case "$1" in
        *.*) printf '%s-%s.%s\n' "${1%.*}" "$2" "${1##*.}" ;;
        *) printf '%s-%s\n' "$1" "$2" ;;
    esac
}

# A run that reports success without the file it was asked for is a failed run: the client can only
# say it issued the command, and the point of the dispatch is the JSON on disk. The check is about a
# script other than the suite's, because the suite asserts its own four outputs.
if [ "$SCRIPT" != "extractor-client.lua" ]; then
    if [ -n "$LOCALES" ]; then
        # A locale run is checked per locale: no file, another locale's name in it, or nothing
        # resolved at all (which is what a wrong language code looks like) is a failed run.
        for code in $LOCALES; do
            file="$RUN_DIR/$(locale_output "$OUTPUT_FILE" "$code")"
            if [ ! -s "$file" ]; then
                echo "error: the $code run wrote no $(basename "$file") in $RUN_DIR" >&2
                if [ "$status" -eq 0 ]; then status=1; fi
                continue
            fi
            command -v jq > /dev/null || continue
            if ! jq -e --arg locale "$code" '
                    has("metadata") and has("items") and has("blocks") and has("fluids") and has("entities")
                    and (.metadata.locale == $locale)
                    and (.metadata.minecraftLocale == ($locale | ascii_downcase))
                    and (.metadata.schemaVersion == "2")
                ' "$file" > /dev/null 2>&1; then
                echo "error: $(basename "$file") is not a $code extraction (roots, metadata.locale, schemaVersion)" >&2
                if [ "$status" -eq 0 ]; then status=1; fi
                continue
            fi
            counts="$(jq -r '[.. | objects | select(has("translated"))]
                    | "\(length) \([.[] | select(.translated == true)] | length)"' "$file")"
            total="${counts%% *}"
            translated="${counts##* }"
            if [ "$total" -eq 0 ] || [ "$translated" -eq 0 ]; then
                echo "error: $(basename "$file") resolved $translated of $total names — a wrong locale code looks like this" >&2
                if [ "$status" -eq 0 ]; then status=1; fi
            fi
        done
    elif [ ! -s "$RUN_DIR/$OUTPUT_FILE" ]; then
        echo "error: the run wrote no $OUTPUT_FILE in $RUN_DIR" >&2
        if [ "$status" -eq 0 ]; then status=1; fi
    elif command -v jq > /dev/null \
            && ! jq -e 'has("metadata") and has("items") and has("blocks") and has("fluids") and has("entities")' \
                "$RUN_DIR/$OUTPUT_FILE" > /dev/null 2>&1; then
        echo "error: $OUTPUT_FILE is not a registry extraction (a root registry is missing)" >&2
        if [ "$status" -eq 0 ]; then status=1; fi
    fi
fi

# The shared lifecycle workflow uploads `src/<loader>/mod/run` as this leg's artifact, but the run
# happens inside the Minecheck checkout. Copy what a failure is diagnosed from there - before the
# status decides the exit code, so a red run still leaves its log and JSON behind.
REPORT_DIR="${REPORT_DIR:-}"
if [ -n "$REPORT_DIR" ]; then
    [[ "$REPORT_DIR" = /* ]] || REPORT_DIR="$ROOT/$REPORT_DIR"
    rm -rf "$REPORT_DIR"
    mkdir -p "$REPORT_DIR/client/config/minecheck" "$REPORT_DIR/client/logs"
    cp "$RUN_DIR"/extractor-*.json "$REPORT_DIR/client/" 2>/dev/null || true
    cp "$RUN_DIR/extractor-spaced file.json" "$REPORT_DIR/client/" 2>/dev/null || true
    cp "$RUN_DIR"/registry.json "$REPORT_DIR/client/" 2>/dev/null || true
    cp "$RUN_DIR"/config/minecheck/run-result.json "$REPORT_DIR/client/config/minecheck/" 2>/dev/null || true
    cp "$RUN_DIR"/logs/latest.log "$REPORT_DIR/client/logs/" 2>/dev/null || true
    echo "reports   : copied to $REPORT_DIR/client"
fi

exit "$status"
