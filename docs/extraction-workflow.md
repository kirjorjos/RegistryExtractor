# Manual extraction run

`Extract registry` (`.github/workflows/extract-registry.yml`) runs the extractor in a headless client on
this branch's Minecraft version, loads the Integrated mods it is asked for, writes the registry JSON, and
either uploads it as a run artifact or commits it into the repository that consumes it. It exists because
the JSON changes with the mod set: the suite's golden is one vanilla-shaped run, and refreshing the docs'
copy should not need a local Prism instance.

It is a tool, not a check. It only runs on `workflow_dispatch`, nothing in CI validates it, and it is
repo-owned: `kirjorjos/ci` syncs the checks a pull request runs and has no template for this.

The workflow lives on every `<version>/development` branch, and `1.19.2/v0.1.0` put it on
`1.19.2/main` as well, so the Actions sidebar lists it for the default branch and "Run workflow"
needs no branch switching to appear. A dispatch still runs the copy on the branch you pick, so the
branch selector decides which line's extractor, harness and mods are used.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `minecraft_version` | this branch | Which line to extract for. Empty uses the dispatched branch; naming another checks out `<version>/development`, and a line with no such branch fails. |
| `mods` | `all` | `all` (the whole suite), `vanilla` (the extractor alone), or a comma-separated subset — see below |
| `locale` | — | Empty keeps today's single extraction. `all` resolves the eleven docs locales, or name a comma-separated list of docs codes (`cs_CZ,fr_FR`) — see [Locales](#locales-and-the-artifact-format) |
| `mode` | `artifact` | `artifact` uploads the file; `commit` writes it into the target repository |
| `output_path` | — | Required for `commit`: the file's path inside the target repository, which must already exist there |
| `target_repo` | `kirjorjos/Unofficial-Integrated-Dynamics-Docs-and-Tools` | `owner/name` a commit writes to |
| `target_ref` | `main` | Branch in the target repository the commit lands on |

## The mod selection

`tests/minecheck/integrated-suite.txt` is the list a selection is validated against, so a slug that is
not an Integrated mod this repository knows fails the run instead of extracting a vanilla registry.

- `all` (default) — every mod in that file.
- `vanilla` — none of them; useful for a baseline registry.
- `integrated-dynamics,integrated-terminals` — any subset of the file.

CyclopsCore and CommonCapabilities are libraries rather than suite members, and every Integrated mod
declares them, so `fetch-mods.sh` adds them whenever the selection is not vanilla. Versions are the newest
build each CyclopsMC package publishes for this branch's Minecraft: the point is what the mods say today.
`mods.txt` in the artifact records the versions the run actually loaded, so a regeneration can be traced
back to them.

The jars are the `-deobf` artifacts from the CyclopsMC GitHub Packages maven, not the launcher jars from
Modrinth. The harness boots a development client, which runs Mojang mapping names, while a launcher jar is
remapped into Forge's production namespace: loading one there fails during mod construction with
`NoSuchMethodError` on `m_`-prefixed methods. The deobf artifact is the same mod in development names, so it
loads as-is. Reading it needs a token that can see the packages — the workflow grants its own
`GITHUB_TOKEN` `packages: read`.

Because the deobf artifact *is* the development view, libraries the published jar relocates into itself
are not inside it: the mod's build declares them on its own development classpath (`modLib`, e.g. re2j for
Integrated Dynamics). `fetch-mods.sh` writes those coordinates to `libs.txt` beside the jars and the run
puts them on the client classpath, since a library jar in `mods/` has no mod metadata and Forge skips it.

## What a run produces

The `registry-extraction-<minecraft>` artifact holds the JSON (`extractor-registry.json`), the resolved
`mods.txt` and the client's `latest.log`. The upload is `if: always()`, so a failed extraction still leaves
the log it is diagnosed from. With a `locale` input the file set is one `extractor-registry-<locale>.json`
per locale instead, and the artifact is named `registry-extraction-<minecraft>-<locale>` (a list uses `+`
between the codes).

A `mode: commit` run replaces `output_path` in `target_repo@target_ref` and pushes one commit,
`chore: update <output_path> for Minecraft <version>`. It refuses to create the file: the rule is that this
updates a registry the target repository already commits, so a path that does not exist there is a wrong
input. When the run produces exactly what is already committed, it reports that and commits nothing.
Because one commit replaces one file, commit mode needs `locale` empty or a single code; a list or `all`
is refused (dispatch once per locale instead).

Committing needs the `EXTRACTION_TOKEN` secret — a fine-grained token with `contents: write` on the target
repository only — because the workflow's own token can read this repository and nothing else. Artifact mode
needs no secret.

## Running it locally

```bash
GITHUB_TOKEN="$(gh auth token)" \
tests/minecheck/fetch-mods.sh --select all --dest build/extraction/mods
MODS_EXTRA_DIR=build/extraction/mods \
SCRIPT=extract-registry.lua \
REPORT_DIR=build/extraction/run \
tests/minecheck/run.sh
```

The result lands at `build/extraction/run/client/extractor-registry.json`. Set `LOCALES=fr_FR` (or
`LOCALES=all`, or a comma-separated list) to write per-locale files instead; `run.sh` puts the codes in
`extractor-locales.txt` in the client's run dir, which is what the extractor reads. JDK 17 is required. `run.sh`
resolves the Minecheck harness itself: `MINECHECK_DIR` when set, a `../Minecheck` checkout, or a clone of
the branch's `<version>/v<min>` tag.

`run.sh` also checks the result, so a client that exits cleanly without writing the file still fails: the
file has to exist and, when `jq` is available, carry the `metadata`/`items`/`blocks`/`fluids`/`entities`
roots. A `mods/` folder is shared between runs, so the previous run's mod set is removed before the new
jars are copied in. A locale run is checked per locale, as described under [What the run
checks](#what-the-run-checks).

## Locales and the artifact format

With `locale` empty, a run is the suite's: one `extractor-registry.json` with `schemaVersion` `"1"`,
the shape the golden pins, named after the client's own language and carrying no locale metadata. Asking
for locales writes one file per locale instead, with `schemaVersion` `"2"`: the extraction runs once and
each locale is resolved from the `translationKey` every entry already carries and the loaded mods'
packaged `assets/<namespace>/lang/<code>.json` files, so adding a language costs no second client boot.

### Locale codes

The input and the artifact use the docs site's codes; the language file is the same code lowercased.
The eleven are `cs_CZ`, `de_DE`, `en_US`, `fr_FR`, `ja_JP`, `ko_KR`, `nl_NL`, `pt_BR`, `ru_RU`, `sv_SE`,
`zh_CN`.

| Docs | Minecraft language file |
|---|---|
| `cs_CZ` | `assets/<namespace>/lang/cs_cz.json` |
| `en_US` | `assets/<namespace>/lang/en_us.json` |
| `fr_FR` | `assets/<namespace>/lang/fr_fr.json` |
| `ja_JP` | `assets/<namespace>/lang/ja_jp.json` |
| `zh_CN` | `assets/<namespace>/lang/zh_cn.json` |

`metadata.locale` is the docs code and `metadata.minecraftLocale` the language file code, so a consumer
never has to guess which convention it is reading.

### A locale artifact

`metadata` adds three fields to the ones the extraction already writes:

| Field | Meaning |
|---|---|
| `locale` | The docs code this file was resolved for. |
| `minecraftLocale` | The language file code (`fr_fr`) the names came from. |
| `mods` | `[{id, name, version}]` for every loaded mod, sorted by id: the builds that produced the file, carried inside it. |

The roots are the same four objects keyed by namespace and then path. Per entry:

| Field | Meaning |
|---|---|
| `name` | The entry's name in this locale, or `null` when the mod ships no translation for its `translationKey`. |
| `translated` | `false` when `name` is `null` for that reason. The site shows nothing rather than the key text or the English name. |
| `translationKey` | The key `name` was resolved from. |
| `modName` | The owning mod's display name, on every kind (entities also keep it as `mod`). |
| `tooltip` | Items only, and only in the `en_US` file: a tooltip is a rendered string, not a key, so any other locale reports `null` rather than English text. |
| others | Unchanged from the extraction (`mod`, per-kind fields, tags, …). |

So a per-locale file carries everything the site reads for `registry.<kind>.<namespace>.<path>.name`,
`.modName` and `.tooltip`, and an entry upstream does not translate is marked rather than falling back
to English. Copying `extractor-registry-<locale>.json` into the docs as
`src/lib/i18n/lang/<locale>/registry.json` is the whole import.

### What the run checks

A locale run fails when it wrote no file for a requested locale, when that file's `metadata.locale` is
not the code that was asked for, or when it resolved zero names across all four roots — what a wrong
language code looks like. The `en_US`-only file still has to carry the five roots, as before. The checks
that need `jq` are skipped, with the rest of `run.sh`'s, when it is not installed.

## Limits

- **Forge only.** The harness boots a Forge client, so a branch that also builds for Fabric is not
  exercised here; the suite has the same limit.
- **No pinning.** Mods are resolved to the newest matching build each run. `mods.txt` records what was
  used, which is what a reproducibility question needs answered, but two runs a week apart can differ.
- **A client run needs a world.** The script creates one (`extractor_registry`) and joins it, so a run is
  a few minutes of headless client, not a server-console command.
