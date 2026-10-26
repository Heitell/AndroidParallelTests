"""Таблицы для docs/report.md из docs/results/bench.csv: медиана по прогонам, ускорение к A1.

python3 -I scripts/report_table.py [docs/results/bench.csv]
"""
import csv
import os
import statistics
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

NAMES = {
    "A": "A. Gradle `connectedDebugAndroidTest`",
    "B": "B. Шардинг раннера",
    "Bo": "B + Orchestrator",
    "C": "C. Gradle Managed Devices",
    "Catd": "C на образе ATD",
    "D": "D. Marathon (по тесту)",
    "Dc": "D. Marathon (по классу)",
    "E": "E. Своя очередь",
}
ORDER = ["A", "B", "Bo", "C", "Catd", "D", "Dc", "E"]


def num(v):
    return float(v) if v not in ("", None) else None


def med(rows, key):
    vals = [num(r[key]) for r in rows if num(r[key]) is not None]
    return statistics.median(vals) if vals else None


def fmt(v, digits=0):
    return "—" if v is None else f"{v:.{digits}f}"


def main(path):
    rows = list(csv.DictReader(open(path)))
    bad = [r for r in rows if r["ok"] != "1"]
    good = [r for r in rows if r["ok"] == "1"]
    groups = {}
    for r in good:
        groups.setdefault((r["method"], int(r["n"])), []).append(r)
    base = med(groups[("A", 1)], "total_s") if ("A", 1) in groups else None
    base_run = med(groups[("A", 1)], "run_s") if ("A", 1) in groups else None

    print(f"Прогонов: {len(rows)}, годных: {len(good)}, отбракованных: {len(bad)}")
    for r in bad:
        print(f"  отбракован: круг {r['rep']} {r['case']} tests={r['tests']} runs={r['runs']} failed={r['failed']}")
    print()
    print("| Способ | Потоков | Всего, с | Загрузка, с | Установка, с | Тесты, с | Ускорение (всего) | Ускорение (тесты) | Прогонов |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for m in ORDER:
        for n in range(1, 5):
            g = groups.get((m, n))
            if not g:
                continue
            total, run = med(g, "total_s"), med(g, "run_s")
            sp = f"×{base / total:.2f}" if base and total else "—"
            spr = f"×{base_run / run:.2f}" if base_run and run else "—"
            print(f"| {NAMES[m]} | {n} | {fmt(total)} | {fmt(med(g, 'boot_s'))} | {fmt(med(g, 'install_s'), 1)} | "
                  f"{fmt(run)} | {sp} | {spr} | {len(g)} |")
    print()
    print("Разброс по прогонам (мин–макс «всего», с):")
    for m in ORDER:
        for n in range(1, 5):
            g = groups.get((m, n))
            if g:
                t = sorted(num(r["total_s"]) for r in g)
                print(f"  {m}{n}: {t[0]:.0f}–{t[-1]:.0f}")
    print()
    print("| Способ | Потоков | Самое загруженное устройство, с | Самое свободное, с | Процессор мака, средн./пик % | Свободная память, мин. % |")
    print("|---|---:|---:|---:|---:|---:|")
    for m in ORDER:
        for n in range(1, 5):
            g = groups.get((m, n))
            if not g:
                continue
            print(f"| {NAMES[m]} | {n} | {fmt(med(g, 'busiest_s'))} | {fmt(med(g, 'idlest_s'))} | "
                  f"{fmt(med(g, 'cpu_avg_pct'))} / {fmt(med(g, 'cpu_max_pct'))} | {fmt(med(g, 'mem_free_min_pct'))} |")
    shards = {}
    for r in good:
        if r.get("shards"):
            shards.setdefault(r["case"], set()).add(r["shards"])
    if shards:
        print()
        print("Тестов на шард у GMD:", {k: sorted(v) for k, v in sorted(shards.items())})


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs/results/bench.csv"))
