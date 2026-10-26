"""Один запуск `am instrument` на одном устройстве с записью JUnit XML.

Время каждого теста считается по моменту, когда раннер сообщил о старте и о конце теста.

python3 -I scripts/instrument.py --serial emulator-5554 --out shard.xml [--orchestrator] [-- -e key value ...]
"""
import argparse
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

TEST_ID = "com.example.catalog.test"
RUNNER = f"{TEST_ID}/androidx.test.runner.AndroidJUnitRunner"
ORCHESTRATOR = "androidx.test.orchestrator/.AndroidTestOrchestrator"

# Коды статуса из androidx.test: 1 начало, 0 успех, -1 ошибка, -2 падение, -3 пропуск, -4 допущение.
START, OK, ERROR, FAILURE, IGNORED, ASSUMPTION = 1, 0, -1, -2, -3, -4


def command(serial, extra, orchestrator):
    if orchestrator:
        inner = ["-e", "targetInstrumentation", RUNNER, *extra]
        cmd = ["am", "instrument", "-r", "-w", *inner, ORCHESTRATOR]
        # Orchestrator запускает каждый тест через test-services в отдельном процессе.
        return ["adb", "-s", serial, "shell", "CLASSPATH=$(pm path androidx.test.services) app_process / "
                "androidx.test.services.shellexecutor.ShellMain " + " ".join(cmd)]
    return ["adb", "-s", serial, "shell", "am", "instrument", "-r", "-w", *extra, RUNNER]


def run(serial, extra, orchestrator):
    proc = subprocess.Popen(command(serial, extra, orchestrator), stdout=subprocess.PIPE, text=True, bufsize=1)
    results, status, started, stack = [], {}, {}, []
    tail = []
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
            elif None not in name:
                elapsed = time.time() - started.pop(name, time.time())
                results.append((name[0], name[1], code, elapsed, "\n".join(stack)))
            status, stack = {}, []
    proc.wait()
    crashed = not any(l.startswith("INSTRUMENTATION_CODE: -1") for l in tail)
    return results, crashed, tail


def write_xml(path, serial, results):
    suite = ET.Element("testsuite", name=serial, tests=str(len(results)),
                       failures=str(sum(1 for r in results if r[2] in (FAILURE, ERROR))))
    for cls, name, code, elapsed, stack in results:
        tc = ET.SubElement(suite, "testcase", classname=cls, name=name, time=f"{elapsed:.3f}")
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
    p.add_argument("extra", nargs="*")
    a = p.parse_args()
    results, crashed, tail = run(a.serial, a.extra, a.orchestrator)
    write_xml(a.out, a.serial, results)
    bad = sum(1 for r in results if r[2] in (FAILURE, ERROR))
    print(f"{a.serial}: tests={len(results)} failed={bad}" + (" INSTRUMENTATION CRASHED" if crashed else ""))
    if crashed:
        print("\n".join(tail), file=sys.stderr)
    sys.exit(1 if bad or crashed else 0)


if __name__ == "__main__":
    main()
