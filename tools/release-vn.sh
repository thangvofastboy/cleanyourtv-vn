#!/usr/bin/env bash
# This fork's release: build the launcher in Docker (tests, lint, release APK), copy it to
# dist/cleanyourtv-vn-<versionCode>.apk and print the `gh release create` command. It never runs gh itself.
# The in-app update (Launcher settings > Check for updates) looks for exactly that asset name on the latest
# release of thangvofastboy/cleanyourtv-vn, tagged v<versionName>. Bump versionCode and versionName in
# launcher/app/build.gradle first: the TV only offers a versionCode above the installed one.
# Always build with the same Docker volume (cleanyourtv-gradle): its debug key signs every release, and an update
# signed with another key is refused by the launcher and by Android (tools/docker-build.md, "The signing key").
#
# Usage: tools/release-vn.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$ROOT/launcher/app/build.gradle"
CODE=$(sed -n 's/^[[:space:]]*versionCode[[:space:]][[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' "$GRADLE" | head -n 1)
NAME=$(sed -n "s/^[[:space:]]*versionName[[:space:]][[:space:]]*'\([^']*\)'[[:space:]]*\$/\1/p" "$GRADLE" | head -n 1)
[ -n "$CODE" ] && [ -n "$NAME" ] || { echo "ERROR: could not read versionCode/versionName from $GRADLE" >&2; exit 1; }
# The certificate every release of this fork is signed with (apksigner, "Signer #1 certificate SHA-256 digest").
# A build signed with anything else could never update the TV, so it is refused here instead of being published.
PINNED_CERT_SHA256=c7bbb787073101e9f5444b87bd66b760bd44c2259771f94a4d653f198d70c23d
IMAGE=cleanyourtv-android-build
# These move the key docker-build.sh signs with away from the cleanyourtv-gradle volume.
for v in HOST_KEY_DIR GRADLE_VOLUME KEY_DIR; do
  if [ -n "${!v:-}" ]; then
    echo "ERROR: $v is set; a release is signed only with the default key in the cleanyourtv-gradle volume." >&2
    exit 1
  fi
done
IMAGE="$IMAGE" "$ROOT/tools/docker-build.sh" launcher testReleaseUnitTest lintRelease assembleRelease
APK="$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "ERROR: no APK at $APK" >&2; exit 1; }
mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/cleanyourtv-vn-$CODE.apk"
cp "$APK" "$OUT"
CERTS=$(docker run --rm --platform linux/amd64 --entrypoint /opt/android-sdk/build-tools/34.0.0/apksigner \
  -v "$ROOT/dist:/dist:ro" "$IMAGE" verify --print-certs "/dist/cleanyourtv-vn-$CODE.apk" | grep 'certificate SHA-256 digest' || true)
if [ "$CERTS" != "Signer #1 certificate SHA-256 digest: $PINNED_CERT_SHA256" ]; then
  rm -f "$OUT"
  echo "ERROR: $OUT is not signed with the pinned certificate (or by more than one signer); removed it." >&2
  echo "apksigner says: ${CERTS:-nothing}" >&2
  echo "Restore the key from /home/thangvo/.cleanyourtv/signing/debug.keystore into the cleanyourtv-gradle volume" >&2
  echo "(/gradle/.android/debug.keystore) and build again." >&2
  exit 1
fi
echo "Built $OUT ($(wc -c < "$OUT") B), versionCode $CODE, versionName $NAME, signed with the pinned certificate."
echo "Signing key backup: /home/thangvo/.cleanyourtv/signing/ (keep it; without it no later release can update the TV)."
echo "Before publishing: install this APK over adb on the TV first and check Home comes back (work/FORK-CHANGES.md)."
echo "Publish it with:"
echo "  gh release create v$NAME dist/cleanyourtv-vn-$CODE.apk --repo thangvofastboy/cleanyourtv-vn --title v$NAME --notes \"cleanyourtv-vn $NAME\""
