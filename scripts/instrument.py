"""Один запуск `am instrument` на одном устройстве с записью JUnit XML.

Время каждого теста считается по моменту, когда раннер сообщил о старте и о конце теста.
Тест, который начался и не закончился (упал процесс, оборвалась связь, сработал сторож),
записывается красным. Если запуск оборвался, у <testsuite> стоит атрибут aborted с причиной.

Если запуск оборвался, а эмулятор жив, тесты этого запуска, до которых дело не дошло, запускаются
ещё раз отдельным запуском (дозапуск). Без него падение процесса или сторож теряют остаток шарда.

python3 -I scripts/instrument.py --serial emulator-5554 --out shard.xml [--orchestrator] [--timeout 1800] [--no-resume] [-- -e key value ...]
"""
import argparse
import os
import shlex
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tools import ADB, APP_ID, TEST_ID, alive, list_tests  # noqa: E402

RUNNER = f"{TEST_ID}/androidx.test.runner.AndroidJUnitRunner"
ORCHESTRATOR = "androidx.test.orchestrator/.AndroidTestOrchestrator"

# Коды статуса из androidx.test: 1 начало, 0 успех, -1 ошибка, -2 падение, -3 пропуск, -4 допущение.
START, OK, ERROR, FAILURE, IGNORED, ASSUMPTION = 1, 0, -1, -2, -3, -4


def command(serial, extra, orchestrator):
    """adb shell склеивает аргументы в строку для оболочки устройства, поэтому каждый — в кавычках:
    иначе `EnclosedTest$Details` превратится в `EnclosedTest`."""
    if orchestrator:
        inner = ["-e", "targetInstrumentation", RUNNER, *extra]
        cmd = ["am", "instrument", "-r", "-w", *inner, ORCHESTRATOR]
        # Orchestrator запускает каждый тест через test-services в отдельном процессе.
        line = ("CLASSPATH=$(pm path androidx.test.services) app_process / "
                "androidx.test.services.shellexecutor.ShellMain " + shlex.join(cmd))
    else:
        line = shlex.join(["am", "instrument", "-r", "-w", *extra, RUNNER])
    return [ADB, "-s", serial, "shell", line]


def force_stop(serial):
    for pkg in (APP_ID, TEST_ID, "androidx.test.orchestrator"):
        subprocess.run([ADB, "-s", serial, "shell", "am", "force-stop", pkg],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)


def run(serial, extra, orchestrator, timeout):
    proc = subprocess.Popen(command(serial, extra, orchestrator), stdout=subprocess.PIPE, text=True, bufsize=1)
    fired = threading.Event()

    def watchdog():
        if proc.poll() is None:
            fired.set()
            proc.kill()

    timer = threading.Timer(timeout, watchdog)
    timer.start()
    results, status, started, stack, tail = [], {}, {}, [], []
    try:
        for line in proc.stdout:
            line = line.rstrip("\r\n")
            tail = (tail + [line])[-20:]
            if line.startswith("INSTRUMENTATION_STATUS: "):
                key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
                status[key] = value
                stack = [value] if key == "stack" else stack
            elif stack and not line.startswith("INSTRUMENTATION_"):
                stack.append(line)
            elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
                code = int(line.split(": ", 1)[1])
                name = (status.get("class"), status.get("test"))
                if code == START:
                    started[name] = time.time()
                elif name[0] is not None:
                    elapsed = time.time() - started.pop(name, time.time())
                    results.append((name[0], name[1], code, elapsed, "\n".join(stack)))
                status, stack = {}, []
        proc.wait()
    finally:
        timer.cancel()

    if fired.is_set():
        reason = f"остановлено сторожем через {timeout} с"
        force_stop(serial)
    elif proc.returncode != 0:
        reason = f"adb завершился с кодом {proc.returncode}"
    elif not any(l.startswith("INSTRUMENTATION_CODE: -1") for l in tail):
        reason = "инструментация оборвана: " + next(
            (l for l in reversed(tail) if "shortMsg" in l or "INSTRUMENTATION_RESULT" in l), "без причины")
    else:
        reason = ""
    # Тесты, у которых был старт и не было конца, не теряем: пишем красными.
    for (cls, name), t0 in started.items():
        results.append((cls, name, ERROR, time.time() - t0, "нет конца теста: " + (reason or "причина неизвестна")))
    return results, reason, tail


# Ключи отбора тестов: на дозапуске они заменяются списком оставшихся тестов.
SELECTION = {"class", "notClass", "package", "notPackage", "numShards", "shardIndex", "tests_regex"}


def without_selection(extra):
    out, i = [], 0
    while i < len(extra):
        if extra[i] == "-e" and i + 1 < len(extra) and extra[i + 1] in SELECTION:
            i += 3
            continue
        out.append(extra[i])
        i += 1
    return out


def run_with_resume(serial, extra, orchestrator, timeout, resume):
    results, reason, tail = run(serial, extra, orchestrator, timeout)
    reasons = [reason] if reason else []
    while reason and resume and alive(serial):
        done = {(cls, name or "null") for cls, name, *_ in results}
        try:
            left = [t for t in list_tests(serial, extra) if t not in done]
        except RuntimeError as e:
            reasons.append(f"дозапуск невозможен: {e}")
            break
        if not left:
            break
        # Раннер сравнивает имя метода без набора параметров: для search[3] запускаем search
        # и оставляем только те наборы, которых ещё нет в отчёте.
        targets = sorted({cls if m == "null" else f"{cls}#{m.split('[', 1)[0]}" for cls, m in left})
        print(f"{serial}: дозапуск {len(left)} тестов после обрыва", file=sys.stderr)
        more, reason, tail = run(serial, without_selection(extra) + ["-e", "class", ",".join(targets)],
                                 orchestrator, timeout)
        more = [r for r in more if (r[0], r[1] or "null") in set(left)]
        if not more:
            reasons.append("дозапуск не дал ни одного результата")
            break
        results += more
        if reason:
            reasons.append("дозапуск: " + reason)
    return results, "; ".join(reasons), tail


def write_xml(path, serial, results, reason):
    suite = ET.Element("testsuite", name=serial, tests=str(len(results)),
                       failures=str(sum(1 for r in results if r[2] in (FAILURE, ERROR))),
                       skipped=str(sum(1 for r in results if r[2] in (IGNORED, ASSUMPTION))))
    if reason:
        suite.set("aborted", reason)
    for cls, name, code, elapsed, stack in results:
        # Класс, пропущенный целиком через @Ignore, раннер сообщает без имени теста.
        tc = ET.SubElement(suite, "testcase", classname=cls, name=name or "null", time=f"{elapsed:.3f}")
        if code in (FAILURE, ERROR):
            ET.SubElement(tc, "failure").text = stack
        elif code in (IGNORED, ASSUMPTION):
            ET.SubElement(tc, "skipped")
    ET.ElementTree(suite).write(path, encoding="utf-8", xml_declaration=True)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--serial", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--orchestrator", action="store_true")
    p.add_argument("--timeout", type=int, default=int(os.environ.get("INSTRUMENT_TIMEOUT", 1800)),
                   help="сторож: через сколько секунд остановить запуск")
    p.add_argument("--no-resume", action="store_true", help="не дозапускать тесты после обрыва")
    p.add_argument("extra", nargs="*")
    a = p.parse_args()
    results, reason, tail = run_with_resume(a.serial, a.extra, a.orchestrator, a.timeout, not a.no_resume)
    write_xml(a.out, a.serial, results, reason)
    bad = sum(1 for r in results if r[2] in (FAILURE, ERROR))
    print(f"{a.serial}: tests={len(results)} failed={bad}" + (f" ОБОРВАН: {reason}" if reason else ""))
    if reason:
        print("\n".join(tail), file=sys.stderr)
    sys.exit(1 if bad or reason else 0)


if __name__ == "__main__":
    main()
