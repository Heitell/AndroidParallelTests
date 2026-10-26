"""Хронология прогона по устройствам: когда появилось, загрузилось, когда шли тесты.

Раз в секунду опрашивает adb. Пишет CSV: serial,seen,booted,tests_start,tests_end (секунды от старта).
python3 -I scripts/monitor.py OUT.csv   (останавливается по SIGTERM)
"""
import signal
import subprocess
import sys
import time

APP = "com.example.catalog"
stop = False


def on_term(*_):
    global stop
    stop = True


def sh(*args):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=5).stdout
    except subprocess.TimeoutExpired:
        return ""


def main(out):
    signal.signal(signal.SIGTERM, on_term)
    t0 = time.time()
    info = {}
    while not stop:
        now = round(time.time() - t0, 1)
        for line in sh("adb", "devices").splitlines():
            if not line.startswith("emulator-") or not line.endswith("\tdevice"):
                continue
            s = line.split()[0]
            d = info.setdefault(s, {"seen": now, "booted": None, "tests_start": None, "tests_end": None})
            if d["booted"] is None and sh("adb", "-s", s, "shell", "getprop", "sys.boot_completed").strip() == "1":
                d["booted"] = now
            if d["booted"] is not None and sh("adb", "-s", s, "shell", "pidof", APP).strip():
                d["tests_start"] = d["tests_start"] if d["tests_start"] is not None else now
                d["tests_end"] = now
        time.sleep(1)
    with open(out, "w") as f:
        f.write("serial,seen,booted,tests_start,tests_end\n")
        for s, d in sorted(info.items()):
            f.write(",".join([s] + ["" if d[k] is None else str(d[k]) for k in ("seen", "booted", "tests_start", "tests_end")]) + "\n")


if __name__ == "__main__":
    main(sys.argv[1])
