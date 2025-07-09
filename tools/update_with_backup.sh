#!/bin/sh
set -e
APK="$1"
if [ -z "$APK" ]; then
  echo "Usage: $0 path/to/app.apk"
  exit 1
fi

TMPDIR=$(mktemp -d)
BACKUP=$TMPDIR/futo_prefs.tar

echo "Exporting preferences..."
adb shell "run-as org.futo.inputmethod.latin tar cf /sdcard/futo_prefs.tar shared_prefs" || true
adb pull /sdcard/futo_prefs.tar "$BACKUP" >/dev/null 2>&1 || true

echo "Uninstalling previous version..."
adb uninstall org.futo.inputmethod.latin >/dev/null 2>&1 || true

echo "Installing new version..."
adb install "$APK"

if [ -f "$BACKUP" ]; then
  echo "Restoring preferences..."
  adb push "$BACKUP" /sdcard/futo_prefs.tar >/dev/null
  adb shell "run-as org.futo.inputmethod.latin tar xf /sdcard/futo_prefs.tar"
fi

echo "Done"
