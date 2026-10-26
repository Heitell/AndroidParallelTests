"""Замер всех способов запуска. Пишет одну строку CSV на прогон.

python3 -I scripts/bench.py --reps 3 --out docs/results/bench.csv [--cases A1,B2,...]

Порядок: круг по всем случаям, потом следующий круг (A B C D E, A B C D E, ...),
между прогонами пауза на остывание. Перед каждым прогоном свои эмуляторы
перезапускаются со снимка bench, у Gradle Managed Devices — их собственный снимок.

Случаи (буква — способ, число — потоков):
  A  connectedDebugAndroidTest на одном эмуляторе
  B  шардинг AndroidJUnitRunner (numShards/shardIndex), Bo — то же с Orchestrator
  C  Gradle Managed Devices с шардингом, Catd — то же на облегчённом образе ATD
  D  Marathon (по тесту за команду), Dc — Marathon пакетами по классу
  E  своя очередь: свободный эмулятор берёт следующий класс
"""
import argparse
import csv
import glob
import os
import shutil
import signal
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = os.path.join(ROOT, "scripts")
# Шардинг GMD сломан в AGP 9.4.0 и 9.4.1 (каждый шард гоняет все тесты), поэтому C — на 9.3.3.
GMD_AGP = "9.3.3"
GMD_TREE = os.path.join(os.path.dirname(ROOT), "AndroidParallelTests-gmd")
EXPECTED = 40

DEFAULT_CASES = ("A1 B2 B3 B4 C1 C2 C3 C4 D1 D2 D3 D4 E2 E3 E4 "
                 "Bo2 Bo4 Catd2 Catd4 Dc2 Dc4").split()

FIELDS = ["rep", "case", "method", "n", "boot_s", "install_s", "run_s", "total_s",
          "tests", "runs", "failed", "duplicates", "test_sum_s", "busiest_s", "idlest_s",
          "cpu_avg_pct", "cpu_max_pct", "mem_free_min_pct", "started_at"]


def sh(cmd, cwd=ROOT, env=None, log=None):
    t0 = time.time()
    with open(log or os.devnull, "a") as out:
        rc = subprocess.run(cmd, cwd=cwd, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
    return rc, time.time() - t0


class Sampler:
    """Загрузка процессора всей машины и свободная память раз в 5 с."""

    def __init__(self):
        self.cpu, self.mem, self._stop = [], [], threading.Event()
        self.ncpu = os.cpu_count()

    def _run(self):
        while not self._stop.is_set():
            out = subprocess.run(["ps", "-A", "-o", "pcpu="], capture_output=True, text=True).stdout
            self.cpu.append(sum(float(x) for x in out.split()) / self.ncpu)
            mp = subprocess.run(["memory_pressure"], capture_output=True, text=True).stdout
            for line in mp.splitlines():
                if "free percentage" in line:
                    self.mem.append(int(line.rsplit(" ", 1)[1].rstrip("%")))
            self._stop.wait(5)

    def __enter__(self):
        self.t = threading.Thread(target=self._run, daemon=True)
        self.t.start()
        return self

    def __exit__(self, *a):
        self._stop.set()
        self.t.join()


def junit(paths):
    """tests, runs, failed, duplicates, сумма времени, самое загруженное и самое свободное устройство."""
    seen, per_device, failed, total = {}, {}, 0, 0.0
    for f in paths:
        root = ET.parse(f).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for suite in suites:
            for tc in suite.iter("testcase"):
                name = f'{tc.get("classname")}#{tc.get("name")}'
                seen[name] = seen.get(name, 0) + 1
                t = float(tc.get("time") or 0)
                total += t
                dev = suite.get("name") or "?"
                per_device[dev] = per_device.get(dev, 0.0) + t
                if tc.find("failure") is not None or tc.find("error") is not None:
                    failed += 1
    loads = sorted(per_device.values())
    return {
        "tests": len(seen), "runs": sum(seen.values()), "failed": failed,
        "duplicates": sum(1 for c in seen.values() if c > 1), "test_sum_s": round(total, 1),
        "busiest_s": round(loads[-1], 1) if loads else "", "idlest_s": round(loads[0], 1) if loads else "",
    }


def gmd_runs(result_dir):
    """Сколько тестов реально стартовало на всех шардах GMD (по журналам раннера)."""
    runs, per_shard = 0, []
    for shard in sorted(glob.glob(os.path.join(result_dir, "shard_*"))) or [result_dir]:
        n = 0
        for f in glob.glob(os.path.join(shard, "logcat-*.txt")):
            with open(f, errors="replace") as fh:
                n += sum(1 for line in fh if "TestRunner: started:" in line)
        per_shard.append(n)
        runs += n
    return runs, per_shard


def ensure_gmd_tree(log):
    if not os.path.isdir(GMD_TREE):
        subprocess.run(["git", "worktree", "add", "--detach", GMD_TREE, "HEAD"], cwd=ROOT, check=True)
    toml = os.path.join(GMD_TREE, "gradle", "libs.versions.toml")
    text = open(toml).read()
    import re
    open(toml, "w").write(re.sub(r'^agp = ".*"$', f'agp = "{GMD_AGP}"', text, flags=re.M))
    shutil.copy(os.path.join(ROOT, "local.properties"), GMD_TREE)
    rc, _ = sh(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest", "-q"], cwd=GMD_TREE, log=log)
    if rc:
        sys.exit("сборка копии для GMD не удалась, см. " + log)
    # Прогрев: первый запуск каждого образа скачивает его, создаёт эмулятор и снимок. В замер не идёт.
    for device in ("pixel35gmd", "pixel35atd"):
        marker = os.path.join(GMD_TREE, f".warm-{device}")
        if os.path.exists(marker):
            continue
        rc, t = sh(["./gradlew", f":app:{device}DebugAndroidTest", "-q",
                    "-Pandroid.testoptions.manageddevices.emulator.gpu=host",
                    "-Pandroid.testInstrumentationRunnerArguments.class=com.example.catalog.tests.CatalogListTest"],
                   cwd=GMD_TREE, log=log)
        if rc:
            sys.exit(f"прогрев {device} не удался, см. {log}")
        print(f"прогрев {device}: {t:.0f} с", flush=True)
        open(marker, "w").close()


def emulators(n, log):
    sh([os.path.join(S, "emulators.sh"), "stop"], log=log)
    if n == 0:
        return 0.0
    rc, t = sh([os.path.join(S, "emulators.sh"), "start", str(n)], log=log)
    if rc:
        raise RuntimeError(f"эмуляторы не поднялись (n={n})")
    return t


def uninstall(log):
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        if line.endswith("\tdevice"):
            for pkg in ("com.example.catalog", "com.example.catalog.test",
                        "androidx.test.orchestrator", "androidx.test.services"):
                sh(["adb", "-s", line.split()[0], "uninstall", pkg], log=log)


def run_case(case, out_dir, log):
    method = case.rstrip("0123456789")
    n = int(case[len(method):])
    row = {"case": case, "method": method, "n": n, "boot_s": "", "install_s": ""}
    os.makedirs(out_dir, exist_ok=True)

    if method in ("C", "Catd"):
        emulators(0, log)
        device = "pixel35gmd" if method == "C" else "pixel35atd"
        cmd = ["./gradlew", f":app:{device}DebugAndroidTest", "--rerun", "--offline", "-q",
               "-Pandroid.testoptions.manageddevices.emulator.gpu=host"]
        if n > 1:
            cmd.append(f"-Pandroid.experimental.androidTest.numManagedDeviceShards={n}")
        timeline = os.path.join(out_dir, "timeline.csv")
        mon = subprocess.Popen([sys.executable, "-I", os.path.join(S, "monitor.py"), timeline])
        rc, t = sh(cmd, cwd=GMD_TREE, log=log)
        mon.send_signal(signal.SIGTERM)
        mon.wait()
        res = os.path.join(GMD_TREE, "app/build/outputs/androidTest-results/managedDevice/debug", device)
        shutil.copytree(res, os.path.join(out_dir, "results"), dirs_exist_ok=True)
        row.update(junit(glob.glob(os.path.join(res, "TEST-*.xml"))))
        row["runs"], per_shard = gmd_runs(res)
        row["busiest_s"], row["idlest_s"] = "", ""
        with open(timeline) as f:
            rows = list(csv.DictReader(f))
        starts = [float(r["tests_start"]) for r in rows if r["tests_start"]]
        ends = [float(r["tests_end"]) for r in rows if r["tests_end"]]
        if starts:
            # До первого запуска приложения — загрузка и установка, дальше — тесты.
            row["boot_s"] = round(min(starts), 1)
            row["run_s"] = round(max(ends) - min(starts), 1)
        else:
            row["run_s"] = round(t, 1)
        row["total_s"] = round(t, 1)
        row["shards"] = per_shard
        return rc, row

    row["boot_s"] = round(emulators(n, log), 1)
    uninstall(log)
    if method == "A":
        rc, t = sh(["./gradlew", ":app:connectedDebugAndroidTest", "--offline", "-q"], log=log)
        res = os.path.join(ROOT, "app/build/outputs/androidTest-results/connected/debug")
        shutil.copytree(res, os.path.join(out_dir, "results"), dirs_exist_ok=True)
        xmls = glob.glob(os.path.join(res, "TEST-*.xml"))
    elif method in ("D", "Dc"):
        batching = "isolate" if method == "D" else "class-name"
        rc, t = sh([os.path.join(S, "run_marathon.sh"), out_dir, batching], log=log)
        xmls = glob.glob(os.path.join(out_dir, "tests", "**", "*.xml"), recursive=True)
    else:
        orch = method == "Bo"
        rc_i, ti = sh([os.path.join(S, "install.sh")] + (["--orchestrator"] if orch else []), log=log)
        row["install_s"] = round(ti, 1)
        if method in ("B", "Bo"):
            rc, t = sh([os.path.join(S, "run_shards.sh"), str(n), out_dir] + (["--orchestrator"] if orch else []), log=log)
        else:
            rc, t = sh([sys.executable, "-I", os.path.join(S, "run_queue.py"), str(n), out_dir], log=log)
        rc = rc or rc_i
        xmls = glob.glob(os.path.join(out_dir, "*.xml"))
    row["run_s"] = round(t, 1)
    row["total_s"] = round(row["boot_s"] + (row["install_s"] or 0) + t, 1)
    row.update(junit(xmls))
    if method == "A":
        row["busiest_s"] = row["idlest_s"] = row["test_sum_s"]
    if method in ("D", "Dc"):
        # В отчёте Marathon нет устройства у теста — загрузку по устройствам не считаем.
        row["busiest_s"] = row["idlest_s"] = ""
    return rc, row


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--reps", type=int, default=3)
    p.add_argument("--out", default=os.path.join(ROOT, "docs/results/bench.csv"))
    p.add_argument("--cases", default=",".join(DEFAULT_CASES))
    p.add_argument("--cool", type=int, default=20, help="пауза между прогонами, с")
    a = p.parse_args()
    cases = a.cases.split(",")
    work = os.path.join(ROOT, "bench-out", "bench")
    os.makedirs(work, exist_ok=True)
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    log = os.path.join(work, "bench.log")
    sh(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest", "-q"], log=log)
    if any(c.startswith("C") for c in cases):
        ensure_gmd_tree(log)
    new = not os.path.exists(a.out)
    with open(a.out, "a", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS + ["shards", "ok"], extrasaction="ignore")
        if new:
            w.writeheader()
        for rep in range(1, a.reps + 1):
            for case in cases:
                started = time.strftime("%Y-%m-%d %H:%M:%S")
                with Sampler() as smp:
                    try:
                        rc, row = run_case(case, os.path.join(work, f"rep{rep}", case), log)
                    except RuntimeError as e:
                        rc, row = 1, {"case": case, "method": case.rstrip("0123456789"), "n": case[-1]}
                        print(e, file=sys.stderr)
                row.update(rep=rep, started_at=started,
                           cpu_avg_pct=round(sum(smp.cpu) / len(smp.cpu)) if smp.cpu else "",
                           cpu_max_pct=round(max(smp.cpu)) if smp.cpu else "",
                           mem_free_min_pct=min(smp.mem) if smp.mem else "")
                row["ok"] = int(rc == 0 and row.get("tests") == EXPECTED and row.get("runs") == EXPECTED
                                and row.get("failed") == 0 and row.get("duplicates") == 0)
                w.writerow(row)
                f.flush()
                print(f'rep{rep} {case}: total={row.get("total_s")} run={row.get("run_s")} '
                      f'tests={row.get("tests")} runs={row.get("runs")} failed={row.get("failed")} ok={row["ok"]}',
                      flush=True)
                time.sleep(a.cool)
    sh([os.path.join(S, "emulators.sh"), "stop"], log=log)


if __name__ == "__main__":
    main()
