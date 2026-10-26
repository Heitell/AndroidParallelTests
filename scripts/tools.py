"""Общее для скриптов на Python: путь к adb, список эмуляторов, список тестов от раннера."""
import os
import shlex
import shutil
import subprocess

ANDROID_HOME = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
_sdk_adb = os.path.join(ANDROID_HOME, "platform-tools", "adb")
ADB = _sdk_adb if ANDROID_HOME and os.path.exists(_sdk_adb) else (shutil.which("adb") or "adb")

APP_ID = "com.example.catalog"
TEST_ID = "com.example.catalog.test"
RUNNER = f"{TEST_ID}/androidx.test.runner.AndroidJUnitRunner"
# Эмуляторы этого проекта стоят на портах 5554, 5556, 5558, 5560 (scripts/emulators.sh).
PORTS = (5554, 5556, 5558, 5560)


def devices():
    """Запущенные эмуляторы этого проекта, по порядку портов. Чужие эмуляторы на других портах не берём."""
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True, check=True, timeout=60).stdout
    ours = {f"emulator-{p}" for p in PORTS}
    found = [l.split()[0] for l in out.splitlines() if l.endswith("\tdevice") and l.split()[0] in ours]
    return sorted(found, key=lambda s: int(s.split("-")[1]))


def alive(serial):
    """Эмулятор отвечает adb и готов к работе."""
    try:
        out = subprocess.run([ADB, "-s", serial, "get-state"], capture_output=True, text=True, timeout=30).stdout
    except subprocess.TimeoutExpired:
        return False
    return out.strip() == "device"


def list_tests(serial, extra=()):
    """Тесты, которые раннер запустил бы с этими аргументами, без запуска: режим `-e log true`.
    Возвращает список (класс, метод) в порядке раннера. Падает, если перечисление не дошло до конца."""
    line = shlex.join(["am", "instrument", "-r", "-w", "-e", "log", "true", *extra, RUNNER])
    proc = subprocess.run([ADB, "-s", serial, "shell", line], capture_output=True, text=True, timeout=300)
    out = proc.stdout
    if proc.returncode or "INSTRUMENTATION_CODE: -1" not in out:
        if "Unable to find instrumentation" in out + proc.stderr:
            raise RuntimeError(f"на {serial} не установлен тестовый APK: запустите scripts/install.sh")
        raise RuntimeError(f"раннер не перечислил тесты на {serial}:\n" + out[-2000:])
    tests, status = [], {}
    for raw in out.splitlines():
        raw = raw.rstrip("\r")
        if raw.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = raw[len("INSTRUMENTATION_STATUS: "):].partition("=")
            status[key] = value
        elif raw.startswith("INSTRUMENTATION_STATUS_CODE: "):
            # Пропущенные через @Ignore приходят без статуса начала, поэтому берём любой статус.
            if status.get("class"):
                name = (status["class"], status.get("test", "null"))
                if name not in tests:
                    tests.append(name)
            status = {}
    return tests


if __name__ == "__main__":
    import sys
    if sys.argv[1:2] != ["reference"]:
        sys.exit("python3 -I scripts/tools.py reference [SERIAL] > reference.txt")
    serial = sys.argv[2] if len(sys.argv) > 2 else devices()[0]
    print("\n".join(f"{c}#{m}" for c, m in list_tests(serial)))
