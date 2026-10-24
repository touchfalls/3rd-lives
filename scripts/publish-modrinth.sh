#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 touchfalls
#
# Publishes the Paper jar and the two Fabric jars (1.21.1, 26.2) of a release to Modrinth as three versions.
#
#   scripts/publish-modrinth.sh <version> <modrinth-project-id> [--dry-run]
#
# The token is read from $MODRINTH_TOKEN_FILE (default ~/.config/authsecured/modrinth-token).
# The jars are taken from dist/ and the changelog from docs/release-notes/<version>.md
# (the whole file, without the downloads table). --dry-run prints both requests and sends nothing.
set -euo pipefail

VERSION="${1:?usage: publish-modrinth.sh <version> <modrinth-project-id> [--dry-run]}"
PROJECT_ID="${2:?usage: publish-modrinth.sh <version> <modrinth-project-id> [--dry-run]}"
DRY_RUN="${3:-}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOKEN_FILE="${MODRINTH_TOKEN_FILE:-$HOME/.config/authsecured/modrinth-token}"
NOTES="$ROOT/docs/release-notes/$VERSION.md"
PAPER_JAR="$ROOT/dist/paper/lifesmp-$VERSION.jar"
FABRIC_JAR="$ROOT/dist/fabric/lifesmp-fabric-$VERSION.jar"
FABRIC_26_JAR="$ROOT/dist/fabric-26/lifesmp-fabric-26-$VERSION.jar"
FABRIC_API_PROJECT_ID="P7dR8mSH"
USER_AGENT="touchfalls/3rd-lives publish-modrinth.sh"

for f in "$NOTES" "$PAPER_JAR" "$FABRIC_JAR" "$FABRIC_26_JAR"; do
  [ -f "$f" ] || { echo "missing: $f (run ./gradlew clean dist first)" >&2; exit 1; }
done

# Modrinth shows the changelog next to the version; drop the downloads table that only fits GitHub.
CHANGELOG="$(python3 - "$NOTES" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
text = re.sub(r"## Downloads.*?(?=\n## )", "", text, flags=re.S)
print(text.strip())
PY
)"

build_data() { # $1=name suffix, $2=loaders json, $3=dependencies json, $4=game versions json
  python3 - "$VERSION" "$PROJECT_ID" "$1" "$2" "$3" "$CHANGELOG" "$4" <<'PY'
import json, sys
version, project, platform, loaders, deps, changelog, games = sys.argv[1:8]
print(json.dumps({
    "project_id": project,
    "name": f"Life SMP {version} ({platform})",
    "version_number": version,
    "changelog": changelog,
    "dependencies": json.loads(deps),
    "game_versions": json.loads(games),
    "version_type": "release",
    "loaders": json.loads(loaders),
    "featured": True,
    "file_parts": ["file"],
    "primary_file": "file",
}, ensure_ascii=False, indent=2))
PY
}

FABRIC_API_DEP="[{\"project_id\":\"$FABRIC_API_PROJECT_ID\",\"dependency_type\":\"required\"}]"
PAPER_DATA="$(build_data Paper '["paper","purpur"]' '[]' '["1.21.1","26.2"]')"
FABRIC_DATA="$(build_data 'Fabric 1.21.1' '["fabric"]' "$FABRIC_API_DEP" '["1.21.1"]')"
FABRIC_26_DATA="$(build_data 'Fabric 26.2' '["fabric"]' "$FABRIC_API_DEP" '["26.2"]')"

if [ "$DRY_RUN" = "--dry-run" ]; then
  echo "== Paper ($PAPER_JAR)"; echo "$PAPER_DATA"
  echo "== Fabric 1.21.1 ($FABRIC_JAR)"; echo "$FABRIC_DATA"
  echo "== Fabric 26.2 ($FABRIC_26_JAR)"; echo "$FABRIC_26_DATA"
  exit 0
fi

[ -r "$TOKEN_FILE" ] || { echo "cannot read token file: $TOKEN_FILE" >&2; exit 1; }
TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"

upload() { # $1=data json, $2=jar
  curl --fail-with-body -sS -X POST "https://api.modrinth.com/v2/version" \
    -H "Authorization: $TOKEN" -H "User-Agent: $USER_AGENT" \
    -F "data=$1;type=application/json" \
    -F "file=@$2;type=application/java-archive"
  echo
}

upload "$PAPER_DATA" "$PAPER_JAR"
upload "$FABRIC_DATA" "$FABRIC_JAR"
upload "$FABRIC_26_DATA" "$FABRIC_26_JAR"
echo "Published $VERSION to Modrinth project $PROJECT_ID"
