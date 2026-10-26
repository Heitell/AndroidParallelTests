#!/usr/bin/env bash
# Поставить приложение и тестовый APK на все запущенные эмуляторы (параллельно).
# С --orchestrator ещё и orchestrator с test-services из кеша Gradle.
set -euo pipefail
source "$(dirname "$0")/env.sh"

extra=()
if [[ "${1:-}" == "--orchestrator" ]]; then
  # Версии — из gradle/libs.versions.toml; APK скачиваются с Google Maven один раз в bench-out/apk.
  version() { sed -n "s/^$1 = \"\(.*\)\"$/\1/p" "$ROOT/gradle/libs.versions.toml"; }
  fetch() {  # группа-путь артефакт версия
    local file="$ROOT/bench-out/apk/$2-$3.apk"
    [[ -s "$file" ]] || { mkdir -p "$(dirname "$file")"
      curl -fsSL -o "$file" "https://dl.google.com/android/maven2/$1/$2/$3/$2-$3.apk"; }
    echo "$file"
  }
  extra+=("$(fetch androidx/test orchestrator "$(version orchestrator)")")
  extra+=("$(fetch androidx/test/services test-services "$(version testServices)")")
fi

pids=()
for d in $(devices); do
  (
    for apk in "$APP_APK" "$TEST_APK"; do
      adb -s "$d" install -r -t -g "$apk" >/dev/null
    done
    # Как у Gradle: без --force-queryable тестовый процесс не видит Orchestrator.
    for apk in ${extra[@]+"${extra[@]}"}; do
      adb -s "$d" install -r -t -g --force-queryable "$apk" >/dev/null
    done
  ) &
  pids+=($!)
done
(( ${#pids[@]} > 0 )) || { echo "нет запущенных эмуляторов" >&2; exit 1; }
for p in "${pids[@]}"; do wait "$p"; done
