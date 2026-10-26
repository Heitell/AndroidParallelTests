"""Сводка по JUnit XML: число тестов, падения, время по классам, дубли.

python3 -I scripts/junit_summary.py <файл или каталог> [...]
"""
import collections
import glob
import os
import sys
import xml.etree.ElementTree as ET


def files(paths):
    for p in paths:
        if os.path.isdir(p):
            yield from sorted(glob.glob(os.path.join(p, "**", "*.xml"), recursive=True))
        else:
            yield p


def main(paths):
    seen = collections.Counter()
    per_class = collections.Counter()
    failed = []
    for f in files(paths):
        root = ET.parse(f).getroot()
        for tc in root.iter("testcase"):
            name = f'{tc.get("classname")}#{tc.get("name")}'
            seen[name] += 1
            per_class[tc.get("classname").rsplit(".", 1)[-1]] += float(tc.get("time") or 0)
            if tc.find("failure") is not None or tc.find("error") is not None:
                failed.append(name)
    dups = [n for n, c in seen.items() if c > 1]
    print(f"tests={len(seen)} runs={sum(seen.values())} failed={len(failed)} duplicates={len(dups)}")
    print("class seconds:", {k: round(v, 1) for k, v in sorted(per_class.items())}, "sum", round(sum(per_class.values()), 1))
    for n in failed:
        print("FAILED", n)
    for n in dups:
        print("DUPLICATE", n)


if __name__ == "__main__":
    main(sys.argv[1:])
