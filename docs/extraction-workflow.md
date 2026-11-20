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
declares them, so `fetch-mods.sh` adds them whenever the selection is not vanilla. Versions are Modrinth's
latest Forge build for this branch's Minecraft: the point is what the mods say today. `mods.txt` in the
artifact records the versions the run actually loaded, so a regeneration can be traced back to them.

## What a run produces

The `registry-extraction-<minecraft>` artifact holds the JSON (`extractor-registry.json`), the resolved
`mods.txt` and the client's `latest.log`. The upload is `if: always()`, so a failed extraction still leaves
the log it is diagnosed from.

A `mode: commit` run replaces `output_path` in `target_repo@target_ref` and pushes one commit,
`chore: update <output_path> for Minecraft <version>`. It refuses to create the file: the rule is that this
updates a registry the target repository already commits, so a path that does not exist there is a wrong
input. When the run produces exactly what is already committed, it reports that and commits nothing.

Committing needs the `EXTRACTION_TOKEN` secret — a fine-grained token with `contents: write` on the target
repository only — because the workflow's own token can read this repository and nothing else. Artifact mode
needs no secret.

## Running it locally

```bash
tests/minecheck/fetch-mods.sh --select all --dest build/extraction/mods
MODS_EXTRA_DIR=build/extraction/mods \
SCRIPT=extract-registry.lua \
REPORT_DIR=build/extraction/run \
tests/minecheck/run.sh
```

The result lands at `build/extraction/run/client/extractor-registry.json`. JDK 17 is required. `run.sh`
resolves the Minecheck harness itself: `MINECHECK_DIR` when set, a `../Minecheck` checkout, or a clone of
the branch's `<version>/v<min>` tag.

`run.sh` also checks the result, so a client that exits cleanly without writing the file still fails: the
file has to exist and, when `jq` is available, carry the `metadata`/`items`/`blocks`/`fluids`/`entities`
roots. A `mods/` folder is shared between runs, so the previous run's mod set is removed before the new
jars are copied in.

## Limits

- **Forge only.** The harness boots a Forge client, so a branch that also builds for Fabric is not
  exercised here; the suite has the same limit.
- **No pinning.** Mods are resolved to the newest matching build each run. `mods.txt` records what was
  used, which is what a reproducibility question needs answered, but two runs a week apart can differ.
- **A client run needs a world.** The script creates one (`extractor_registry`) and joins it, so a run is
  a few minutes of headless client, not a server-console command.
