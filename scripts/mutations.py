"""Матрица поломок: каждая поломка ставится поверх коммита, все способы прогоняются по разу,
сверка проверяет, что покраснели ровно ожидаемые тесты и ни один тест не потерялся.

python3 -I scripts/mutations.py [--only assert,crash,kill,hang,param] [--tag -retry1] [--cases A1,B4,...] [--out docs/results/mutations.csv]

Рабочее дерево должно быть чистым: откат идёт через `git checkout` изменённых файлов.
Копия проекта для способа C получает ту же поломку сама (bench.ensure_gmd_tree).
"""
import argparse
import os
import signal
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = "com.example.catalog.tests."
SUPPORT = "app/src/androidTest/java/com/example/catalog/support/MockServer.kt"
DETAILS = "app/src/main/java/com/example/catalog/ui/DetailsScreen.kt"
EXTRA = "app/src/androidTest/java/com/example/catalog/tests/extra/"
CONTENT = "            is DetailsState.Content -> Column(Modifier.padding(16.dp)) {\n"

MUTATIONS = {
    # Проверка не сходится: у товара №2 заглушка отдаёт другое название.
    "assert": {
        "edits": [(SUPPORT, 'fun item(id: Int, title: String = "Item $id"',
                   'fun item(id: Int, title: String = if (id == 2) "Broken 2" else "Item $id"')],
        "failed": [T + "CatalogListTest#showsItemTitle", T + "DetailsTest#detailsShowTitleAndPrice",
                   T + "extra.JavaStyleTest#opensDetails", T + "extra.AnnotatedTest#mediumOnly",
                   T + "extra.AnnotatedTest#largeOnly"],
    },
    # Исключение при показе карточки товара №4. Его ловит правило Compose: тест красный, процесс жив.
    "crash": {
        "edits": [(DETAILS, CONTENT, CONTENT + '                check(s.item.id != 4) { "поломка: падение на товаре 4" }\n')],
        "failed": [T + "DetailsTest#detailsShowOutOfStock", T + "extra.EspressoInteropTest#reopenDifferentItemAfterBack",
                   T + "extra.JavaStyleTest#detailsShowPrice"],
    },
    # Процесс приложения умирает, когда показывает карточку товара №4: исключение в отдельном потоке
    # правило Compose не ловит, раннер помечает текущий тест и процесс гибнет вместе с остатком запуска.
    "kill": {
        "edits": [(DETAILS, CONTENT, CONTENT + '                if (s.item.id == 4) Thread { error("поломка: процесс упал на товаре 4") }.start()\n')],
        "failed": [T + "DetailsTest#detailsShowOutOfStock", T + "extra.EspressoInteropTest#reopenDifferentItemAfterBack",
                   T + "extra.JavaStyleTest#detailsShowPrice"],
    },
    # Главный поток приложения встаёт на 25 минут, когда показывает карточку товара №5.
    "hang": {
        "edits": [(DETAILS, CONTENT, CONTENT + "                if (s.item.id == 5) Thread.sleep(25 * 60 * 1000L)\n")],
        "failed": [T + "extra.ActivityRuleTest#opensDetailsOfTappedItem", T + "extra.LongFlowTest#fullJourneyOnSlowNetwork"],
        # Сторож одного запуска am instrument и предел шага замера, с.
        "env": {"INSTRUMENT_TIMEOUT": "600", "BENCH_STEP_TIMEOUT": "1500"},
    },
    # В параметризованных тестах неверен один набор данных: краснеть должен ровно он.
    "param": {
        "edits": [(EXTRA + "ParameterizedSearchTest.kt", 'arrayOf("two words", "two words")', 'arrayOf("two words", "two  words")'),
                  (EXTRA + "InjectedParamsTest.kt", 'CENTS(19.99, "$19.99")', 'CENTS(19.99, "$19.98")')],
        "failed": [T + "extra.ParameterizedSearchTest#search[3]", T + "extra.InjectedParamsTest#listPrice[CENTS]"],
    },
}
CASES = "A1,B4,Bo4,C4,D4,Dc4,E4"


def apply(edits):
    for path, old, new in edits:
        full = os.path.join(ROOT, path)
        text = open(full).read()
        if text.count(old) != 1:
            raise SystemExit(f"поломка не встала: {path}: «{old[:60]}» найдено {text.count(old)} раз")
        open(full, "w").write(text.replace(old, new))


def revert(edits):
    subprocess.run(["git", "checkout", "--", *sorted({p for p, _, _ in edits})], cwd=ROOT, check=True)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--only", default=",".join(MUTATIONS))
    p.add_argument("--cases", default=CASES)
    p.add_argument("--out", default=os.path.join(ROOT, "docs/results/mutations.csv"))
    p.add_argument("--tag", default="", help="метка пробы: добавляется к имени CSV и каталога, чтобы не затереть основной прогон")
    a = p.parse_args()
    dirty = subprocess.run(["git", "status", "--porcelain", "--", "app", "gradle"], cwd=ROOT,
                           capture_output=True, text=True).stdout
    if dirty:
        sys.exit("в app/ или gradle/ есть незакоммиченные правки — поломки ставятся только поверх коммита:\n" + dirty)
    for name in a.only.split(","):
        m = MUTATIONS[name]
        print(f"== поломка {name}: ждём красными {len(m['failed'])}", flush=True)
        apply(m["edits"])
        # Если замер оборвут, откат всё равно нужен: иначе поломка останется в рабочей копии.
        signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
        try:
            env = {**os.environ, **m.get("env", {})}
            # Строка CSV помечается поломкой через отдельный каталог и колонку case в журнале.
            subprocess.run([sys.executable, "-I", os.path.join(ROOT, "scripts", "bench.py"), "--reps", "1",
                            "--cool", "10", "--cases", a.cases, "--out", a.out.replace(".csv", f"-{name}{a.tag}.csv"),
                            "--work", os.path.join(ROOT, "bench-out", "mutations", name + a.tag),
                            "--expect-failed", ",".join(m["failed"])], cwd=ROOT, env=env, check=False)
        finally:
            revert(m["edits"])


if __name__ == "__main__":
    main()
