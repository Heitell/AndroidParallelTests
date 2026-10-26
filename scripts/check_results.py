"""Сверка результатов прогона с эталоном: каждый тест ровно один раз, пропуски — пропусками,
красные — ровно те, что ожидались.

Эталон — список тестов от самого раннера (`tools.list_tests`, режим `-e log true`), по строке
`класс#метод` в файле. Ожидаемые пропуски заданы ниже (SKIPPED), ожидаемые падения — аргументом.

python3 -I scripts/check_results.py --reference REF.txt [--expect-failed A#b,C#d] XML_OR_DIR...
"""
import argparse
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

# Тесты, которые должны быть пропущены (@Ignore и assume). Класс, пропущенный целиком,
# раннер сообщает одним тестом без имени — отсюда `#null`.
SKIPPED = {
    "com.example.catalog.tests.extra.SkippedTest#ignoredOne",
    "com.example.catalog.tests.extra.SkippedTest#ignoredTwo",
    "com.example.catalog.tests.extra.SkippedTest#assumptionFailsBeforeWork",
    "com.example.catalog.tests.extra.SkippedTest#assumptionFailsAfterLaunch",
    "com.example.catalog.tests.extra.IgnoredClassTest#null",
}


def xml_files(paths):
    out = []
    for p in paths:
        if os.path.isdir(p):
            out += glob.glob(os.path.join(p, "**", "*.xml"), recursive=True)
        else:
            out.append(p)
    return sorted(out)


def read(paths):
    """{имя: [исход, ...]} и список оборванных запусков. Исход: passed | failed | skipped."""
    seen, aborted = {}, []
    for f in xml_files(paths):
        root = ET.parse(f).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for suite in suites:
            if suite.get("aborted"):
                aborted.append(f"{os.path.basename(f)}: {suite.get('aborted')}")
            for tc in suite.iter("testcase"):
                name = f'{tc.get("classname")}#{tc.get("name") or "null"}'
                if tc.find("failure") is not None or tc.find("error") is not None:
                    outcome = "failed"
                elif tc.find("skipped") is not None:
                    outcome = "skipped"
                else:
                    outcome = "passed"
                seen.setdefault(name, []).append(outcome)
    return seen, aborted


def check(paths, reference, expect_failed=()):
    expected = set(reference)
    expect_failed = set(expect_failed)
    seen, aborted = read(paths)
    # Класс, пропущенный целиком, раннер сообщает одним тестом `Класс#null`, а некоторые
    # инструменты — каждым методом отдельно. Если все методы такого класса пропущены, это тот же пропуск.
    for name in [n for n in SKIPPED & expected if n.endswith("#null") and n not in seen]:
        cls = name[:-len("#null")]
        methods = [n for n in seen if n.startswith(cls + "#") and n not in expected]
        if methods and all(o == ["skipped"] for n in methods for o in [seen[n]]):
            for n in methods:
                del seen[n]
            seen[name] = ["skipped"]
    final = {n: ("failed" if "failed" in o else o[0]) for n, o in seen.items()}
    r = {
        "expected": len(expected),
        "found": len(set(seen) & expected),
        "runs": sum(len(o) for o in seen.values()),
        # Потерян — должен был выполниться, но его нет в отчёте.
        "missing": sorted(expected - set(seen) - SKIPPED),
        # Пропущенный тест, о котором отчёт молчит: не потеря, но пропуск не виден.
        "skipped_hidden": sorted((expected & SKIPPED) - set(seen)),
        "extra": sorted(set(seen) - expected),
        "duplicates": sorted(n for n, o in seen.items() if len(o) > 1),
        "skipped_wrong": sorted(n for n in SKIPPED & expected
                                if n in final and final[n] != "skipped"),
        "skipped_unexpected": sorted(n for n, o in final.items() if o == "skipped" and n not in SKIPPED),
        "failed": sorted(n for n, o in final.items() if o == "failed"),
        "aborted": aborted,
    }
    r["failed_unexpected"] = sorted(set(r["failed"]) - expect_failed)
    r["failed_missing"] = sorted(expect_failed - set(r["failed"]))
    # Верно — когда каждый тест есть ровно один раз и исход каждого такой, как ожидалось.
    # Оборванный запуск сам по себе не ошибка способа, если ни один тест не потерян.
    # Скрытый пропуск в ok не входит: ни один тест не потерян, отличается только отчёт.
    r["ok"] = not (r["missing"] or r["extra"] or r["duplicates"] or r["skipped_wrong"]
                   or r["skipped_unexpected"] or r["failed_unexpected"] or r["failed_missing"])
    return r


def summary(r):
    keys = ("missing", "skipped_hidden", "extra", "duplicates", "skipped_wrong", "skipped_unexpected",
            "failed_unexpected", "failed_missing")
    parts = [f"{k}={len(r[k])}" for k in keys if r[k]]
    return (f"тестов {r['found']}/{r['expected']}, запусков {r['runs']}, красных {len(r['failed'])}"
            + ("; " + ", ".join(parts) if parts else "") + ("" if r["ok"] else " — НЕ СХОДИТСЯ"))


def read_reference(path):
    return [l.strip() for l in open(path) if l.strip()]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--reference", required=True)
    p.add_argument("--expect-failed", default="")
    p.add_argument("--json", action="store_true")
    p.add_argument("paths", nargs="+")
    a = p.parse_args()
    r = check(a.paths, read_reference(a.reference), [x for x in a.expect_failed.split(",") if x])
    print(json.dumps(r, ensure_ascii=False, indent=1) if a.json else summary(r))
    if not a.json:
        for k in ("missing", "skipped_hidden", "extra", "duplicates", "skipped_wrong", "skipped_unexpected",
                  "failed_unexpected", "failed_missing", "aborted"):
            for n in r[k][:20]:
                print(f"  {k}: {n}")
    sys.exit(0 if r["ok"] else 1)


if __name__ == "__main__":
    main()
