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
import json
import os
import shutil
import signal
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_results import check  # noqa: E402
from tools import ADB, devices, list_tests  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = os.path.join(ROOT, "scripts")
# Шардинг GMD сломан в AGP 9.4.0 и 9.4.1 (каждый шард гоняет все тесты), поэтому C — на 9.3.3.
GMD_AGP = "9.3.3"
GMD_TREE = os.path.join(os.path.dirname(ROOT), "AndroidParallelTests-gmd")
# Сколько ждать один шаг (запуск способа, сборку), с. Дольше — шаг останавливается, прогон красный.
STEP_TIMEOUT = int(os.environ.get("BENCH_STEP_TIMEOUT", 3600))

DEFAULT_CASES = ("A1 B2 B3 B4 C2 C3 C4 D2 D3 D4 E2 E3 E4 "
                 "Bo2 Bo4 Catd2 Catd4 Dc2 Dc4").split()

FIELDS = ["rep", "case", "method", "n", "boot_s", "install_s", "run_s", "total_s",
          "expected", "tests", "runs", "failed", "duplicates", "missing", "skipped_hidden", "extra", "skipped_wrong",
          "failed_unexpected", "failed_missing", "aborted", "test_sum_s", "busiest_s", "idlest_s",
          "cpu_avg_pct", "cpu_max_pct", "mem_free_min_pct", "started_at", "error"]


def sh(cmd, cwd=ROOT, env=None, log=None, timeout=STEP_TIMEOUT):
    """Команда с таймаутом. По таймауту гасится вся группа процессов, код возврата 124."""
    t0 = time.time()
    with open(log or os.devnull, "a") as out:
        proc = subprocess.Popen(cmd, cwd=cwd, env=env, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            rc = proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGKILL)
            proc.wait()
            out.write(f"\n[bench] остановлено по таймауту {timeout} с: {' '.join(cmd)}\n")
            rc = 124
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


def git(*args, cwd=ROOT):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout


def ensure_gmd_tree(log):
    """Копия проекта для C: тот же коммит и те же незакоммиченные правки, что в основной, но AGP 9.3.3."""
    head = git("rev-parse", "HEAD").strip()
    if not os.path.isdir(GMD_TREE):
        git("worktree", "add", "--detach", GMD_TREE, head)
    git("checkout", "--", ".", cwd=GMD_TREE)
    git("checkout", "--detach", head, cwd=GMD_TREE)
    patch = git("diff", "HEAD", "--binary", "--", "app", "gradle")
    if patch:
        subprocess.run(["git", "apply", "--whitespace=nowarn"], cwd=GMD_TREE, input=patch, text=True, check=True)
    for extra in git("ls-files", "--others", "--exclude-standard", "--", "app").split():
        os.makedirs(os.path.dirname(os.path.join(GMD_TREE, extra)), exist_ok=True)
        shutil.copy(os.path.join(ROOT, extra), os.path.join(GMD_TREE, extra))
    diff = subprocess.run(["diff", "-r", os.path.join(ROOT, "app", "src"), os.path.join(GMD_TREE, "app", "src")],
                          capture_output=True, text=True)
    if diff.returncode:
        sys.exit("исходники копии для GMD не совпали с основными:\n" + diff.stdout[:2000])
    toml = os.path.join(GMD_TREE, "gradle", "libs.versions.toml")
    text = open(toml).read()
    import re
    open(toml, "w").write(re.sub(r'^agp = ".*"$', f'agp = "{GMD_AGP}"', text, flags=re.M))
    local = os.path.join(ROOT, "local.properties")
    if os.path.exists(local):
        shutil.copy(local, GMD_TREE)
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
    for serial in devices():
        for pkg in ("com.example.catalog", "com.example.catalog.test",
                    "androidx.test.orchestrator", "androidx.test.services"):
            sh([ADB, "-s", serial, "uninstall", pkg], log=log, timeout=120)


def fresh(path):
    """Пустой каталог: старые XML от прошлых прогонов иначе попадут в сводку."""
    shutil.rmtree(path, ignore_errors=True)
    os.makedirs(path, exist_ok=True)


def run_case(case, out_dir, log):
    """Один прогон. Возвращает строку CSV без сверки и список XML для сверки."""
    method = case.rstrip("0123456789")
    n = int(case[len(method):])
    row = {"case": case, "method": method, "n": n, "boot_s": "", "install_s": ""}
    fresh(out_dir)

    if method in ("C", "Catd"):
        emulators(0, log)
        device = "pixel35gmd" if method == "C" else "pixel35atd"
        res = os.path.join(GMD_TREE, "app/build/outputs/androidTest-results/managedDevice/debug", device)
        shutil.rmtree(res, ignore_errors=True)
        cmd = ["./gradlew", f":app:{device}DebugAndroidTest", "--rerun", "--offline", "-q",
               "-Pandroid.testoptions.manageddevices.emulator.gpu=host"]
        if n > 1:
            cmd.append(f"-Pandroid.experimental.androidTest.numManagedDeviceShards={n}")
        timeline = os.path.join(out_dir, "timeline.csv")
        mon = subprocess.Popen([sys.executable, "-I", os.path.join(S, "monitor.py"), timeline])
        try:
            rc, t = sh(cmd, cwd=GMD_TREE, log=log)
        finally:
            mon.send_signal(signal.SIGTERM)
            mon.wait()
        xmls = []
        if os.path.isdir(res):
            shutil.copytree(res, os.path.join(out_dir, "results"), dirs_exist_ok=True)
            xmls = glob.glob(os.path.join(out_dir, "results", "TEST-*.xml"))
            row["c_runs"], row["shards"] = gmd_runs(res)
        row.update(junit(xmls))
        row["busiest_s"], row["idlest_s"] = "", ""
        rows = list(csv.DictReader(open(timeline))) if os.path.exists(timeline) else []
        starts = [float(r["tests_start"]) for r in rows if r["tests_start"]]
        ends = [float(r["tests_end"]) for r in rows if r["tests_end"]]
        if starts:
            # До первого запуска приложения — загрузка и установка, дальше — тесты.
            row["boot_s"] = round(min(starts), 1)
            row["run_s"] = round(max(ends) - min(starts), 1)
        else:
            row["run_s"] = round(t, 1)
        row["total_s"] = round(t, 1)
        return rc, row, xmls

    row["boot_s"] = round(emulators(n, log), 1)
    uninstall(log)
    if method == "A":
        res = os.path.join(ROOT, "app/build/outputs/androidTest-results/connected/debug")
        shutil.rmtree(res, ignore_errors=True)
        rc, t = sh(["./gradlew", ":app:connectedDebugAndroidTest", "--offline", "-q"], log=log)
        if os.path.isdir(res):
            shutil.copytree(res, os.path.join(out_dir, "results"), dirs_exist_ok=True)
        xmls = glob.glob(os.path.join(out_dir, "results", "TEST-*.xml"))
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
    return rc, row, xmls


def reference(work, log):
    """Эталон: какие тесты есть в собранном APK, по словам самого раннера."""
    emulators(1, log)
    try:
        rc, _ = sh([os.path.join(S, "install.sh")], log=log, timeout=600)
        if rc:
            sys.exit("не удалось поставить APK для эталона, см. " + log)
        tests = [f"{c}#{m}" for c, m in list_tests(devices()[0])]
    finally:
        emulators(0, log)
    path = os.path.join(work, "reference.txt")
    open(path, "w").write("\n".join(tests) + "\n")
    return tests


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--reps", type=int, default=3)
    p.add_argument("--out", default=os.path.join(ROOT, "docs/results/bench.csv"))
    p.add_argument("--cases", default=",".join(DEFAULT_CASES))
    p.add_argument("--cool", type=int, default=20, help="пауза между прогонами, с")
    p.add_argument("--work", default=os.path.join(ROOT, "bench-out", "bench"), help="куда класть журналы и XML")
    p.add_argument("--expect-failed", default="", help="тесты класс#метод через запятую, которые должны упасть")
    a = p.parse_args()
    cases = a.cases.split(",")
    expect_failed = [x for x in a.expect_failed.split(",") if x]
    work = a.work
    os.makedirs(work, exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    log = os.path.join(work, "bench.log")
    rc, _ = sh(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest", "-q"], log=log)
    if rc:
        sys.exit("сборка не удалась, см. " + log)
    try:
        ref = reference(work, log)
        print(f"эталон: {len(ref)} тестов", flush=True)
        if any(c.startswith("C") for c in cases):
            ensure_gmd_tree(log)
        new = not os.path.exists(a.out)
        with open(a.out, "a", newline="") as f:
            w = csv.DictWriter(f, fieldnames=FIELDS + ["c_runs", "shards", "ok"], extrasaction="ignore")
            if new:
                w.writeheader()
            for rep in range(1, a.reps + 1):
                for case in cases:
                    started = time.strftime("%Y-%m-%d %H:%M:%S")
                    xmls, err = [], ""
                    with Sampler() as smp:
                        try:
                            rc, row, xmls = run_case(case, os.path.join(work, f"rep{rep}", case), log)
                        except Exception as e:  # прогон красный, замер идёт дальше
                            rc, row = 1, {"case": case, "method": case.rstrip("0123456789"), "n": case[-1]}
                            err = f"{type(e).__name__}: {e}"
                            print(err, file=sys.stderr, flush=True)
                    r = check(xmls, ref, expect_failed)
                    row.update(rep=rep, started_at=started, error=err, expected=r["expected"], tests=r["found"],
                               runs=r["runs"], failed=len(r["failed"]), duplicates=len(r["duplicates"]),
                               missing=len(r["missing"]), skipped_hidden=len(r["skipped_hidden"]), extra=len(r["extra"]),
                               skipped_wrong=len(r["skipped_wrong"]) + len(r["skipped_unexpected"]),
                               failed_unexpected=len(r["failed_unexpected"]), failed_missing=len(r["failed_missing"]),
                               aborted=len(r["aborted"]),
                               cpu_avg_pct=round(sum(smp.cpu) / len(smp.cpu)) if smp.cpu else "",
                               cpu_max_pct=round(max(smp.cpu)) if smp.cpu else "",
                               mem_free_min_pct=min(smp.mem) if smp.mem else "")
                    # У GMD итоговый XML склеивает повторы, поэтому лишние запуски видны только по журналам шардов.
                    c_dup = row.get("c_runs") not in (None, "") and row["c_runs"] > r["expected"]
                    row["ok"] = int(r["ok"] and not err and not c_dup)
                    # Подробности сверки — рядом с XML прогона.
                    os.makedirs(os.path.join(work, f"rep{rep}", case), exist_ok=True)
                    with open(os.path.join(work, f"rep{rep}", case, "check.json"), "w") as cj:
                        json.dump(r, cj, ensure_ascii=False, indent=1)
                    w.writerow(row)
                    f.flush()
                    print(f'rep{rep} {case}: total={row.get("total_s")} run={row.get("run_s")} '
                          f'tests={r["found"]}/{r["expected"]} runs={r["runs"]} failed={len(r["failed"])} '
                          f'missing={len(r["missing"])} extra={len(r["extra"])} dup={len(r["duplicates"])} '
                          f'aborted={len(r["aborted"])} ok={row["ok"]}', flush=True)
                    time.sleep(a.cool)
    finally:
        sh([os.path.join(S, "emulators.sh"), "stop"], log=log, timeout=300)


if __name__ == "__main__":
    main()
