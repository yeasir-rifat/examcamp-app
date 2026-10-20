#!/usr/bin/env bash
# Step B: capture the app on an emulator/phone with adb, same file names as web_screens.py.
# Usage: tools/parity/app_screens.sh <screen> <light|dark> <en|bn> [WxH-label]   (open the screen on the device first)
# Example: tools/parity/app_screens.sh home light en 390x844
set -euo pipefail
screen=${1:?screen name, e.g. home}; theme=${2:?light|dark}; lang=${3:?en|bn}; size=${4:-390x844}
out=${OUT:-shots/app}; mkdir -p "$out"
adb exec-out screencap -p > "$out/${screen}__${size}__${theme}__${lang}.png"
echo "saved $out/${screen}__${size}__${theme}__${lang}.png"
# Tips: emulator -> `adb shell wm size 1170x2532 && adb shell wm density 480` gives 390x844dp at DPR 3.
#       dark mode: `adb shell cmd uimode night yes`. Hide system bars noise: `adb shell settings put global sysui_demo_allowed 1`.
