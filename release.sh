#!/usr/bin/env bash
# Release routine for Two Heavens.
#
#   ./release.sh
#
# Builds the jar, then lays out two folders in ~/IdeaProjects, both OUTSIDE the
# project, ready to be moved into OneDrive by hand:
#
#   Release <ver>-<mc>_<DD_MM_YY>_<HH_MM>/     mod jar, sources jar, changelog.md
#   <DD_MM_YY>_<HH_MM>/                      <same>.zip, changelog.md,
#                                            CLAUDE.md, notes.md
#
# Everything is derived - version from gradle.properties, timestamp from the jar
# that was actually built, changelog from notes.md - so nothing has to be typed
# twice and nothing can drift.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARENT_DIR="$(dirname "$PROJECT_DIR")"
PROJECT_NAME="$(basename "$PROJECT_DIR")"

# Left out of the zip because they are caches the build regenerates - .gradle alone
# is ~283M. Paths are relative to the project folder. `run` is the test worlds at
# ~75M; add 'run/*' here if the backup does not need them.
ZIP_EXCLUDES=( '.gradle/*' 'build/*' )

cd "$PROJECT_DIR"

echo "==> Building"
./gradlew build -q

VERSION="$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)"
MC_VERSION="$(grep -E '^minecraft_version=' gradle.properties | cut -d= -f2)"

MOD_JAR="build/libs/${PROJECT_NAME%%-*}heavens-${VERSION}+${MC_VERSION}.jar"
MOD_JAR="$(ls build/libs/*"${VERSION}+${MC_VERSION}".jar | grep -v sources | head -1)"
SOURCES_JAR="$(ls build/libs/*"${VERSION}+${MC_VERSION}"-sources.jar | head -1)"

if [[ ! -f "$MOD_JAR" ]]; then
	echo "No mod jar for ${VERSION}+${MC_VERSION} in build/libs" >&2
	exit 1
fi

# Timestamp comes from the JAR, not from `now` - the folder names then describe
# the artifact they contain rather than the moment the script was run.
STAMP_DATE="$(date -r "$MOD_JAR" +%d_%m_%y)"
STAMP_TIME="$(date -r "$MOD_JAR" +%H_%M)"

RELEASE_DIR="$PARENT_DIR/Release ${VERSION}-${MC_VERSION}_${STAMP_DATE}_${STAMP_TIME}"
BACKUP_DIR="$PARENT_DIR/${STAMP_DATE}_${STAMP_TIME}"

echo "==> Changelog for ${VERSION} from notes.md"
CHANGELOG="$(mktemp)"
python3 - "$VERSION" > "$CHANGELOG" <<'PY'
import re, sys
version = sys.argv[1]
notes = open('notes.md').read()
m = re.search(r'^## Changelog: %s\b.*$' % re.escape(version), notes, re.M)
if not m:
	sys.exit("No '## Changelog: %s' section in notes.md" % version)
after = notes[m.end():]
fence = re.search(r'^```\s*\n(.*?)^```\s*$', after, re.S | re.M)
if not fence:
	sys.exit("No fenced changelog block under the %s heading" % version)
sys.stdout.write(fence.group(1).rstrip() + "\n")
PY

if [[ ! -s "$CHANGELOG" ]]; then
	echo "Changelog came out empty - not writing a release" >&2
	exit 1
fi

echo "==> $RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
cp "$MOD_JAR" "$SOURCES_JAR" "$RELEASE_DIR/"
cp "$CHANGELOG" "$RELEASE_DIR/changelog.md"

echo "==> Zipping $PROJECT_NAME"
ZIP_NAME="${STAMP_DATE}_${STAMP_TIME}"
ZIP_PATH="$PARENT_DIR/${ZIP_NAME}.zip"
rm -f "$ZIP_PATH"
( cd "$PARENT_DIR" && zip -rq "$ZIP_PATH" "$PROJECT_NAME" -x "${ZIP_EXCLUDES[@]/#/$PROJECT_NAME/}" )

echo "==> $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"
mv "$ZIP_PATH" "$BACKUP_DIR/"
cp "$CHANGELOG" "$BACKUP_DIR/changelog.md"
rm -f "$CHANGELOG"

# CLAUDE.md and notes.md are copied out LOOSE as well as being inside the zip.
# notes.md is untracked (.git/info/exclude), so no commit carries it and this
# machine holds the only copy - having it readable in the backup folder means it
# can be checked without unpacking 100M, and it is the file most worth not losing.
cp "$PROJECT_DIR/CLAUDE.md" "$BACKUP_DIR/CLAUDE.md"
cp "$PROJECT_DIR/notes.md" "$BACKUP_DIR/notes.md"

echo
echo "Done."
echo "  Release : $RELEASE_DIR"
ls -1sh "$RELEASE_DIR" | sed 's/^/            /'
echo "  Backup  : $BACKUP_DIR"
ls -1sh "$BACKUP_DIR" | sed 's/^/            /'
echo
echo "Move both into OneDrive. CLAUDE.md and notes.md are already in the backup folder."
