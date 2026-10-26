#!/usr/bin/env bash
# Способ B: шардинг AndroidJUnitRunner. На каждом из N эмуляторов своя часть тестов
# (numShards/shardIndex), все части идут одновременно.
#   scripts/run_shards.sh N OUT_DIR [--orchestrator] [-e key value ...]   (аргументы раннера)
set -euo pipefail
source "$(dirname "$0")/env.sh"
n=$1 out=$2
shift 2
orch=""
if [[ "${1:-}" == "--orchestrator" ]]; then orch=--orchestrator; shift; fi
mkdir -p "$out"
# Старые части от прогона с другим числом потоков иначе попадут в сводку.
rm -f "$out"/shard-*.xml
serials=($(devices))
(( ${#serials[@]} >= n )) || { echo "нужно $n эмуляторов, запущено ${#serials[@]}" >&2; exit 1; }
pids=()
for ((i = 0; i < n; i++)); do
  python3 -I "$ROOT/scripts/instrument.py" --serial "${serials[$i]}" --out "$out/shard-$i.xml" $orch \
    -- "$@" -e numShards "$n" -e shardIndex "$i" &
  pids+=($!)
done
rc=0
for p in "${pids[@]}"; do wait "$p" || rc=1; done
exit $rc
