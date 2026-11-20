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
# The jars are the `-deobf` builds from the CyclopsMC GitHub Packages maven. They carry Mojang
# mapping names, which is what the harness's development client runs; the plain jars a launcher
# installs are remapped into Forge's production namespace and die in a dev client with
# `NoSuchMethodError` on `m_`-prefixed methods that only exist in production. Deobf artifacts are
# not published to Modrinth, so reading them needs a token that can see the packages: the
# workflow's own `GITHUB_TOKEN` with `packages: read`, or a personal token with `read:packages`
# when this script is run by hand.
#
# Versions are the newest `<minecraft>-*` each package publishes, because the point of the exercise
# is what the mods say today; `mods.txt` records the versions a run actually loaded.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SUITE_FILE="$ROOT/tests/minecheck/integrated-suite.txt"
MAVEN="${CYCLOPS_MAVEN:-https://maven.pkg.github.com/CyclopsMC/packages}"
TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"

SELECT="all"
DEST=""
MC=""
while [ $# -gt 0 ]; do
    case "$1" in
        --select) SELECT="${2:?--select needs a value}"; shift 2 ;;
        --dest) DEST="${2:?--dest needs a value}"; shift 2 ;;
        --minecraft) MC="${2:?--minecraft needs a value}"; shift 2 ;;
        -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
        *) echo "error: unknown argument $1" >&2; exit 2 ;;
    esac
done

fail() { echo "error: $*" >&2; exit 1; }

[ -n "$DEST" ] || fail "--dest names the directory the jars land in"
[ -f "$SUITE_FILE" ] || fail "$SUITE_FILE is missing, so no selection can be validated"

MC="${MC:-$(sed -n 's/^minecraft_version *= *\([^[:space:]]*\).*/\1/p' "$ROOT/gradle.properties" | head -1)}"
[ -n "$MC" ] || fail "no --minecraft given and gradle.properties declares no minecraft_version"

[ -n "$TOKEN" ] || fail "no GITHUB_TOKEN or GH_TOKEN: the deobf artifacts live in GitHub Packages, which needs a token that may read CyclopsMC's packages"

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

LIBRARIES=(cyclops-core common-capabilities)

projects=("${wanted[@]}")
if [ "${#projects[@]}" -gt 0 ]; then
    projects+=("${LIBRARIES[@]}")
fi
[ "${#projects[@]}" -gt 0 ] || echo "selection: vanilla (the extractor alone)" >&2

# The maven coordinates behind each suite slug. The artifact ids are lowercased `archivesBaseName`s,
# which is what the CyclopsMC publication declares because GitHub rejects uppercase package names.
coordinate() {
    case "$1" in
        cyclops-core)         echo "org.cyclops.cyclopscore cyclopscore" ;;
        common-capabilities)  echo "org.cyclops.commoncapabilities commoncapabilities" ;;
        integrated-dynamics)  echo "org.cyclops.integrateddynamics integrateddynamics" ;;
        integrated-terminals) echo "org.cyclops.integratedterminals integratedterminals" ;;
        integrated-tunnels)   echo "org.cyclops.integratedtunnels integratedtunnels" ;;
        integrated-crafting)  echo "org.cyclops.integratedcrafting integratedcrafting" ;;
        *) return 1 ;;
    esac
}

fetch() { curl -fsSL -H "Authorization: Bearer $TOKEN" "$1"; }

# Resolves `<slug> <version> <group path>/<artifact>` for the newest build of this Minecraft line.
resolve() {
    local slug="$1" group artifact path meta version
    read -r group artifact <<< "$(coordinate "$slug")" \
        || fail "$slug has no CyclopsMC package; the suite and this map disagree"
    path="${group//./\/}/$artifact"
    meta="$(fetch "$MAVEN/$path/maven-metadata.xml")" \
        || fail "could not read $MAVEN/$path/maven-metadata.xml — does the token have packages: read on CyclopsMC/packages?"
    version="$(printf '%s' "$meta" \
        | grep -oE "<version>${MC//./\\.}-[^<]+</version>" \
        | sed -E 's#</?version>##g' \
        | sort -V | tail -1 || true)"
    [ -n "$version" ] || fail "$artifact publishes no $MC build"
    printf '%s %s %s\n' "$slug" "$version" "$path"
}

mkdir -p "$DEST"
rm -f "$DEST"/*.jar
rm -f "$DEST/mods.txt"

: > "$DEST/mods.txt"
for slug in "${projects[@]}"; do
    read -r _ version path <<< "$(resolve "$slug")"
    name="${path##*/}-$version-deobf.jar"
    url="$MAVEN/$path/$version/$name"
    curl -fsSL -H "Authorization: Bearer $TOKEN" -o "$DEST/$name" "$url" \
        || fail "could not download $url"
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
