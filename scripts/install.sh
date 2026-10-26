#!/usr/bin/env bash
# Поставить приложение и тестовый APK на все запущенные эмуляторы (параллельно).
# С --orchestrator ещё и orchestrator с test-services из кеша Gradle.
set -euo pipefail
source "$(dirname "$0")/env.sh"

extra=()
if [[ "${1:-}" == "--orchestrator" ]]; then
  cache="$HOME/.gradle/caches/modules-2/files-2.1"
  extra+=("$(find "$cache/androidx.test/orchestrator" -name 'orchestrator-*.apk' | sort | tail -1)")
  extra+=("$(find "$cache/androidx.test.services/test-services" -name 'test-services-*.apk' | sort | tail -1)")
fi

pids=()
for d in $(devices); do
  (
    for apk in "$APP_APK" "$TEST_APK" ${extra[@]+"${extra[@]}"}; do
      adb -s "$d" install -r -t -g "$apk" >/dev/null
    done
  ) &
  pids+=($!)
done
for p in "${pids[@]}"; do wait "$p"; done
