# AndroidParallelTests

Запуск инструментальных UI-тестов Android в 2–4 потока и сравнение способов. Задача: [#1](https://github.com/Heitell/AndroidParallelTests/issues/1).

Выводы и замеры — в [docs/report.md](docs/report.md).

## Что внутри

- `app/` — приложение «Каталог»: Kotlin, Jetpack Compose, Retrofit + OkHttp. Список товаров с сервера, поиск, карточка товара, обновление потягиванием, экраны ошибки и пустого списка.
- `app/src/androidTest/` — 123 UI-теста. Каждый тест поднимает свой MockWebServer прямо на устройстве и подставляет его адрес в приложение (`AppGraph.configure`). Медленная сеть изображается задержкой ответа (`support/MockServer.kt`, объект `Net`).
  - `tests/` — 40 тестов в шести классах, написаны одинаково: общий базовый класс `BaseTest`.
  - `tests/extra/` — 83 теста, написанные по-разному: правило от экрана, Espresso вместе с Compose, параметризация JUnit и TestParameterInjector, тесты в абстрактном родителе, вложенные классы, `@Ignore` и `assume`, метки `@SmallTest`/`@LargeTest`/`@Smoke`, тест на Java, таймауты JUnit, один длинный сценарий и много коротких. На таких тестах способы запуска ошибаются чаще всего.
- `scripts/` — запуск эмуляторов, каждого способа и замера.
- `marathon/Marathonfile` — настройка Marathon.

Открытый HTTP к `localhost` разрешён только в сборке debug (`app/src/debug`).

## Что нужно

- JDK 21 (Gradle берёт его сам, см. `gradle/gradle-daemon-jvm.properties`).
- Android SDK: платформа 37 для сборки, образ `system-images;android-35;google_apis;arm64-v8a` для эмуляторов, Build Tools 36.
- Четыре эмулятора `pixel35`, `pixel35-2`, `pixel35-3`, `pixel35-4` на этом образе, по 2 ядра и 2 ГБ памяти:
  ```bash
  for name in pixel35 pixel35-2 pixel35-3 pixel35-4; do
    echo no | avdmanager create avd -n "$name" -k "system-images;android-35;google_apis;arm64-v8a" -d pixel
    printf 'hw.cpu.ncore=2\nhw.ramSize=2048\n' >> ~/.android/avd/"$name".avd/config.ini
  done
  ```
- macOS: скрипт замера снимает свободную память командой `memory_pressure`, на других системах этот столбец будет пустым.
- Для способа D — [Marathon](https://github.com/MarathonLabs/marathon) (`brew install malinskiy/tap/marathon`).

Путь к SDK — в `local.properties` (`sdk.dir=...`), в скриптах — переменная `ANDROID_HOME`.

## Как запускать

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

scripts/make_snapshots.sh            # один раз: снимок загрузки «bench» у каждого эмулятора
scripts/emulators.sh start 4         # поднять 4 эмулятора (по одному, с паузой 5 с)
scripts/install.sh                   # поставить APK на все запущенные эмуляторы
mkdir -p out
python3 -I scripts/tools.py reference > out/reference.txt   # эталон: какие тесты есть в APK, по словам раннера

scripts/run_shards.sh 4 out/B4                    # B: шардинг раннера
scripts/run_shards.sh 4 out/Bo4 --orchestrator    # B с Orchestrator (сначала install.sh --orchestrator)
python3 -I scripts/run_queue.py 4 out/E4          # E: своя очередь по классам
scripts/run_marathon.sh out/D4                    # D: Marathon
scripts/emulators.sh stop

# C: Gradle Managed Devices, эмуляторы Gradle поднимает сам.
# На AGP 9.4.0–9.4.1 шардинг сломан (каждый шард гоняет все тесты): перед запуском
# поставьте agp = "9.3.3" в gradle/libs.versions.toml, как делает scripts/bench.py.
./gradlew :app:pixel35gmdDebugAndroidTest -Pandroid.experimental.androidTest.numManagedDeviceShards=4

# Сверка с эталоном: каждый тест ровно один раз, пропуски и падения — как ожидалось
python3 -I scripts/check_results.py --reference out/reference.txt out/B4
```

Замер всех способов: `python3 -I scripts/bench.py --reps 3` (несколько часов). Результат — `docs/results/bench.csv`.

Важно: шардинг Gradle Managed Devices сломан в AGP 9.4.0 и 9.4.1, каждый шард гоняет все тесты. Проект собирается на AGP 9.4.1, а способ C замер гоняет на AGP 9.3.3 в отдельной копии проекта. Подробности — в отчёте.
