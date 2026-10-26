#!/usr/bin/env bash
# Способ D: Marathon на всех запущенных эмуляторах.
#   scripts/run_marathon.sh OUT_DIR [batching]   batching: isolate (по умолчанию) | class-name
set -euo pipefail
source "$(dirname "$0")/env.sh"
out=$(mkdir -p "$1" && cd "$1" && pwd) batching=${2:-isolate}
cfg="$ROOT/marathon/Marathonfile.run"
sed -e "s|^outputDir: .*|outputDir: \"$out\"|" -e "s|type: \"isolate\"|type: \"$batching\"|" \
  "${MARATHONFILE:-$ROOT/marathon/Marathonfile}" >"$cfg"
cd "$ROOT/marathon"
marathon -m "$cfg" >"$out.marathon.log" 2>&1 || { tail -20 "$out.marathon.log" >&2; exit 1; }
