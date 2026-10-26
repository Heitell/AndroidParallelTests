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
