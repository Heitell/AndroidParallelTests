#!/usr/bin/env bash
# Способ B: шардинг AndroidJUnitRunner. На каждом из N эмуляторов своя часть тестов
# (numShards/shardIndex), все части идут одновременно.
#   scripts/run_shards.sh N OUT_DIR [--orchestrator]
set -euo pipefail
source "$(dirname "$0")/env.sh"
n=$1 out=$2 orch=${3:-}
mkdir -p "$out"
serials=($(devices))
(( ${#serials[@]} >= n )) || { echo "нужно $n эмуляторов, запущено ${#serials[@]}" >&2; exit 1; }
pids=()
for ((i = 0; i < n; i++)); do
  python3 -I "$ROOT/scripts/instrument.py" --serial "${serials[$i]}" --out "$out/shard-$i.xml" $orch \
    -- -e numShards "$n" -e shardIndex "$i" &
  pids+=($!)
done
rc=0
for p in "${pids[@]}"; do wait "$p" || rc=1; done
exit $rc
