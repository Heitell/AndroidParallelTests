#!/usr/bin/env bash
# Один раз: снимок «bench» у каждого эмулятора — загружен, фоновая работа закончилась,
# анимации выключены. Замеры стартуют с него, как Gradle Managed Devices со своего снимка.
set -euo pipefail
source "$(dirname "$0")/env.sh"
"$ROOT/scripts/emulators.sh" stop
COLD=1 "$ROOT/scripts/emulators.sh" start 4
echo "фоновая работа утихла за $(IDLE_MAX=600 wait_idle 4) с"
for d in $(devices); do
  animations_off "$d"
  adb -s "$d" emu avd snapshot save bench | tr -d '\r'
done
"$ROOT/scripts/emulators.sh" stop
