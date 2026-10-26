"""Способ E: своя динамическая очередь. Освободившийся эмулятор берёт следующий класс тестов.

python3 -I scripts/run_queue.py N OUT_DIR
"""
import os
import queue
import subprocess
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
RUNNER = "com.example.catalog.test/androidx.test.runner.AndroidJUnitRunner"


def devices():
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True).stdout
    found = [l.split()[0] for l in out.splitlines() if l.startswith("emulator-") and l.endswith("\tdevice")]
    return sorted(found, key=lambda s: int(s.split("-")[1]))


def test_classes(serial):
    """Список классов без запуска тестов: раннер в режиме log только перечисляет их."""
    out = subprocess.run(["adb", "-s", serial, "shell", "am", "instrument", "-r", "-w", "-e", "log", "true", RUNNER],
                         capture_output=True, text=True, check=True).stdout
    classes = []
    for line in out.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: class="):
            cls = line.split("=", 1)[1].strip()
            if cls not in classes:
                classes.append(cls)
    return classes


def main():
    n, out = int(sys.argv[1]), sys.argv[2]
    os.makedirs(out, exist_ok=True)
    serials = devices()[:n]
    if len(serials) < n:
        sys.exit(f"нужно {n} эмуляторов, запущено {len(serials)}")
    work = queue.Queue()
    for cls in test_classes(serials[0]):
        work.put(cls)
    failed = []

    def worker(serial):
        while True:
            try:
                cls = work.get_nowait()
            except queue.Empty:
                return
            xml = os.path.join(out, f"{cls.rsplit('.', 1)[-1]}.xml")
            rc = subprocess.run([sys.executable, "-I", os.path.join(HERE, "instrument.py"), "--serial", serial,
                                 "--out", xml, "--", "-e", "class", cls]).returncode
            if rc:
                failed.append(cls)

    threads = [threading.Thread(target=worker, args=(s,)) for s in serials]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
