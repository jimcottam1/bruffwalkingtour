#!/usr/bin/env bash
# Drive the Android emulator from the command line (Git Bash on Windows).
#
#   scripts/emulator.sh start      boot the AVD and wait until it is ready
#   scripts/emulator.sh install    build the debug APK, install it, grant permissions
#   scripts/emulator.sh geo bruff  fake GPS at the Sean Wall Monument (inside the tour area)
#   scripts/emulator.sh geo away   fake GPS in California (outside — exercises the gate)
#   scripts/emulator.sh launch     open the app on the intro screen
#   scripts/emulator.sh shot NAME  screenshot to $TMPDIR/shots/NAME.png
#   scripts/emulator.sh reset      clear app data (wipes saved walk progress)
#
# Notes: MainActivity is not exported, so `launch` goes through IntroActivity and
# you tap "Start the walk" (bottom of the screen). A freshly booted emulator is
# slow for a minute or so (Bluetooth / Play services ANRs) — wait before judging
# the app.
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
PKG=com.example.bruffwalkingtour
AVD="${AVD:-Pixel_3a_API_34_extension_level_7_x86_64}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"

case "${1:-}" in
  start)
    nohup "$SDK/emulator/emulator.exe" -avd "$AVD" -no-snapshot-save -no-audio \
      -gpu swiftshader_indirect >/dev/null 2>&1 &
    for _ in $(seq 1 60); do
      [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
      sleep 5
    done
    echo "emulator ready" ;;
  install)
    ./gradlew.bat :app:assembleDebug -q
    "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
    for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do
      "$ADB" shell pm grant "$PKG" "android.permission.$p"
    done ;;
  geo)
    case "${2:-}" in
      bruff) "$ADB" emu geo fix -8.5479 52.4777 ;;   # lon lat
      away)  "$ADB" emu geo fix -122.084 37.422 ;;
      *) echo "usage: geo bruff|away" >&2; exit 1 ;;
    esac ;;
  launch) "$ADB" shell am start -n "$PKG/.IntroActivity" ;;
  shot)
    mkdir -p "${TMPDIR:-/tmp}/shots"
    "$ADB" exec-out screencap -p > "${TMPDIR:-/tmp}/shots/${2:?name}.png"
    echo "${TMPDIR:-/tmp}/shots/$2.png" ;;
  reset) "$ADB" shell pm clear "$PKG" ;;
  *) sed -n '2,15p' "$0"; exit 1 ;;
esac
