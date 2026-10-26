#!/usr/bin/env bash
# Поднять N эмуляторов (по одному с паузой: одновременный старт роняет их) или погасить все.
#   scripts/emulators.sh start N
#   scripts/emulators.sh stop
set -euo pipefail
source "$(dirname "$0")/env.sh"

start() {
  local n=$1 log_dir="${LOG_DIR:-$ROOT/bench-out/emulator-logs}"
  (( n >= 1 && n <= ${#AVDS[@]} )) || { echo "можно от 1 до ${#AVDS[@]} эмуляторов" >&2; exit 2; }
  mkdir -p "$log_dir"
  adb start-server >/dev/null 2>&1
  for ((i = 0; i < n; i++)); do
    local port=$((5554 + 2 * i))
    # По умолчанию старт со снимка bench (scripts/make_snapshots.sh), снимок не перезаписывается.
    # COLD=1 — холодный старт.
    local boot=(-snapshot bench -no-snapshot-save)
    [[ "${COLD:-0}" == "1" ]] && boot=(-no-snapshot)
    emulator -avd "${AVDS[$i]}" -port "$port" -no-window -no-audio -no-boot-anim \
      "${boot[@]}" -gpu "${EMU_GPU:-host}" >"$log_dir/${AVDS[$i]}.log" 2>&1 &
    sleep 5
  done
  local deadline=$(( $(date +%s) + 300 ))
  for ((i = 0; i < n; i++)); do
    local serial="emulator-$((5554 + 2 * i))"
    until [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
      (( $(date +%s) < deadline )) || { echo "не загрузился $serial" >&2; stop; exit 1; }
      sleep 2
    done
    animations_off "$serial"
  done
  echo "загружено: $(devices | wc -l | tr -d ' ')"
}

# Гасит только эмуляторы этого проекта (свои AVD на своих портах), чужие не трогает.
stop() {
  local i pattern
  for ((i = 0; i < ${#AVDS[@]}; i++)); do
    adb -s "emulator-${PORTS[$i]}" emu kill >/dev/null 2>&1 || true
  done
  pattern="qemu-system.* -avd ($(IFS='|'; echo "${AVDS[*]}")) -port"
  local deadline=$(( $(date +%s) + 60 ))
  while pgrep -f "$pattern" >/dev/null; do
    (( $(date +%s) < deadline )) || { pkill -f "$pattern" || true; break; }
    sleep 1
  done
}

case "${1:-}" in
  start) start "${2:?сколько эмуляторов}" ;;
  stop) stop ;;
  *) echo "usage: $0 start N | stop" >&2; exit 2 ;;
esac
