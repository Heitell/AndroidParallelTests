# Общие настройки скриптов. Подключается через `source`.
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
APP_ID="com.example.catalog"
TEST_ID="com.example.catalog.test"
RUNNER="$TEST_ID/androidx.test.runner.AndroidJUnitRunner"
AVDS=(pixel35 pixel35-2 pixel35-3 pixel35-4)

now() { python3 -c 'import time; print(f"{time.time():.3f}")'; }

# Серийные номера запущенных эмуляторов, по порядку портов.
devices() { adb devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1}' | sort -t- -k2 -n; }

# Ждать, пока эмуляторы закончат фоновую работу после загрузки: в среднем меньше
# IDLE_CPU % процессора на эмулятор три замера подряд (раз в 5 с), не дольше IDLE_MAX с.
wait_idle() {
  local n=$1 limit=${IDLE_CPU:-25} max=${IDLE_MAX:-300} calm=0 start=$SECONDS
  while (( calm < 3 && SECONDS - start < max )); do
    sleep 5
    local avg
    avg=$(ps -Ao pcpu,command | awk -v n="$n" '/qemu-system/ && !/awk/ {s += $1} END {printf "%d", s / n}')
    if (( avg < limit )); then calm=$((calm + 1)); else calm=0; fi
  done
  echo $((SECONDS - start))
}

animations_off() {
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb -s "$1" shell settings put global "$key" 0
  done
}
