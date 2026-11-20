#!/usr/bin/env bash
# Downloads the mod set one extraction run asks for, and writes down what it resolved.
#
#   tests/minecheck/fetch-mods.sh --select integrated-dynamics,integrated-terminals --dest build/mods
#
# `--select` takes `all` (the default, the whole suite in integrated-suite.txt), `vanilla` (none of
# it), or a comma-separated subset of the suite. CyclopsCore and CommonCapabilities are the
# libraries every Integrated mod declares, so they come along whenever the selection is not vanilla:
# a run without them would fail to load the mod it was asked for.
#
# The versions are Modrinth's latest Forge build for this branch's Minecraft, because the point of
# the exercise is what the mods say today; `mods.txt` records the versions a run actually loaded.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SUITE_FILE="$ROOT/tests/minecheck/integrated-suite.txt"
API="${MODRINTH_API:-https://api.modrinth.com/v2}"
LOADER=forge
LIBRARIES=(cyclops-core common-capabilities)

SELECT="all"
DEST=""
MC=""
while [ $# -gt 0 ]; do
    case "$1" in
        --select) SELECT="${2:?--select needs a value}"; shift 2 ;;
        --dest) DEST="${2:?--dest needs a value}"; shift 2 ;;
        --minecraft) MC="${2:?--minecraft needs a value}"; shift 2 ;;
        -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
        *) echo "error: unknown argument $1" >&2; exit 2 ;;
    esac
done

fail() { echo "error: $*" >&2; exit 1; }

[ -n "$DEST" ] || fail "--dest names the directory the jars land in"
[ -f "$SUITE_FILE" ] || fail "$SUITE_FILE is missing, so no selection can be validated"

MC="${MC:-$(sed -n 's/^minecraft_version *= *\([^[:space:]]*\).*/\1/p' "$ROOT/gradle.properties" | head -1)}"
[ -n "$MC" ] || fail "no --minecraft given and gradle.properties declares no minecraft_version"

mapfile -t SUITE < <(grep -v '^[[:space:]]*#' "$SUITE_FILE" | tr -d '\r' \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | grep -v '^$')
[ "${#SUITE[@]}" -gt 0 ] || fail "$SUITE_FILE lists no mods"

wanted=()
case "$SELECT" in
    ""|all|suite) wanted=("${SUITE[@]}") ;;
    vanilla|none) wanted=() ;;
    *)
        IFS=',' read -r -a requested <<< "$SELECT"
        for mod in "${requested[@]}"; do
            mod="$(printf '%s' "$mod" | tr -d '[:space:]')"
            [ -n "$mod" ] || continue
            if ! printf '%s\n' "${SUITE[@]}" | grep -qx "$mod"; then
                fail "$mod is not an Integrated mod this repository knows: ${SUITE[*]} (or all, vanilla)"
            fi
            wanted+=("$mod")
        done
        [ "${#wanted[@]}" -gt 0 ] || fail "--select named no mod; use vanilla for a run without mods"
        ;;
esac

projects=("${wanted[@]}")
if [ "${#projects[@]}" -gt 0 ]; then
    projects+=("${LIBRARIES[@]}")
fi
[ "${#projects[@]}" -gt 0 ] || echo "selection: vanilla (the extractor alone)" >&2

mkdir -p "$DEST"
rm -f "$DEST"/*.jar
rm -f "$DEST/mods.txt"

# `primary` is Modrinth's flag for the file a launcher should install; a project that marks none
# still has a first file, so that is the fallback rather than "no download".
resolve() {
    local slug="$1" json
    json="$(curl -fsSL --get "$API/project/$slug/version" \
        --data-urlencode "loaders=[\"$LOADER\"]" \
        --data-urlencode "game_versions=[\"$MC\"]")" \
        || fail "Modrinth has no versions for $slug (HTTP failure)"
    jq -er 'sort_by(.date_published) | last as $v
        | "\($v.version_number)\t\((first($v.files[] | select(.primary) | .url)) // $v.files[0].url)"' <<< "$json" \
        || fail "Modrinth lists no Forge $MC version for $slug"
}

: > "$DEST/mods.txt"
for slug in "${projects[@]}"; do
    resolved="$(resolve "$slug")"
    version="${resolved%%$'\t'*}"
    url="${resolved#*$'\t'}"
    name="$(basename "${url%%\?*}")"
    curl -fsSL -o "$DEST/$name" "$url" || fail "could not download $slug $version from $url"
    printf '%s %s %s\n' "$slug" "$version" "$name" >> "$DEST/mods.txt"
    echo "fetched   : $slug $version -> $name"
done

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
        echo "### Mod set"
        echo
        echo "Minecraft $MC, selection: ${SELECT:-all}"
        echo
        sed 's/^/- `/; s/$/`/' "$DEST/mods.txt"
    } >> "$GITHUB_STEP_SUMMARY"
fi

echo "mods      : $(wc -l < "$DEST/mods.txt") jars for Minecraft $MC in $DEST"
