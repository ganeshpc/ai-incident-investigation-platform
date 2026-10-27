#!/bin/sh
# Load the synthetic fixture catalog twice and require the summaries to match.
# The loader builds a new catalog on each run. It does not append, so the
# second summary is identical and no duplicate evidence ids appear.

set -eu

root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
first=$(mktemp)
second=$(mktemp)
trap 'rm -f "$first" "$second"' EXIT

cd "$root/ai-service"
uv run python -m app.fixtures.catalog > "$first"
uv run python -m app.fixtures.catalog > "$second"
cmp "$first" "$second"

uv run python -c 'import json,sys; document=json.load(open(sys.argv[1], encoding="utf-8")); print("loaded", document["evidenceCount"], "evidence records from", len(document["scenarioIds"]), "scenarios")' "$first"
