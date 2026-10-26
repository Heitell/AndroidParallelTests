"""Способ E: своя динамическая очередь. Освободившийся эмулятор берёт следующий класс тестов.

python3 -I scripts/run_queue.py N OUT_DIR [-e key value ...]   (аргументы раннера, например отбор по аннотации)

Если эмулятор отвалился, его класс возвращается в очередь, а сам эмулятор больше ничего не берёт.
"""
import os
import queue
import subprocess
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from tools import ADB, devices, list_tests  # noqa: E402


def alive(serial):
    try:
        out = subprocess.run([ADB, "-s", serial, "get-state"], capture_output=True, text=True, timeout=30).stdout
    except subprocess.TimeoutExpired:
        return False
    return out.strip() == "device"


def main():
    n, out, extra = int(sys.argv[1]), sys.argv[2], sys.argv[3:]
    os.makedirs(out, exist_ok=True)
    serials = devices()[:n]
    if len(serials) < n:
        sys.exit(f"нужно {n} эмуляторов, запущено {len(serials)}")
    classes = list(dict.fromkeys(cls for cls, _ in list_tests(serials[0], extra)))
    if not classes:
        sys.exit("раннер не нашёл ни одного класса тестов: проверьте, что APK установлены")
    work = queue.Queue()
    for cls in classes:
        work.put(cls)
    failed, lost = [], []

    def worker(serial):
        while True:
            try:
                cls = work.get_nowait()
            except queue.Empty:
                return
            # Полное имя: короткие имена у классов из разных пакетов могут совпасть.
            xml = os.path.join(out, cls.replace("$", "_") + ".xml")
            rc = subprocess.run([sys.executable, "-I", os.path.join(HERE, "instrument.py"), "--serial", serial,
                                 "--out", xml, "--", *extra, "-e", "class", cls]).returncode
            if rc and not alive(serial):
                os.remove(xml) if os.path.exists(xml) else None
                work.put(cls)
                lost.append(serial)
                print(f"{serial} отвалился, {cls} вернулся в очередь", file=sys.stderr)
                return
            if rc:
                failed.append(cls)

    threads = [threading.Thread(target=worker, args=(s,)) for s in serials]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    left = []
    while not work.empty():
        left.append(work.get_nowait())
    if left:
        print(f"не запущены (все эмуляторы отвалились): {', '.join(left)}", file=sys.stderr)
    sys.exit(1 if failed or left else 0)


if __name__ == "__main__":
    main()
