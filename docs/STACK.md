# CallMemo — стек и решения

Статус: согласовано 2026-11-07. UI-макеты — отдельно, от пользователя.

## Продукт

Android-звонилка (полная замена штатной) + запись разговора + on-device расшифровка + AI-анализ (DeepSeek) + события/напоминания/заметки. Свой календарь в приложении + опциональная запись в системный календарь.

- Package: `com.alekseevich47.callmemo`
- App name: CallMemo
- Дистрибуция позже: RuStore + APK на сайте; сейчас — только sideload для разработки

## Стек

| Слой | Выбор |
|------|--------|
| Язык / UI | Kotlin, Jetpack Compose, Material 3 |
| Архитектура | Clean Architecture + MVVM |
| DI | Hilt |
| Async | Coroutines + Flow |
| Локальные данные | Room (звонки, транскрипты, заметки, свой календарь), DataStore (настройки) |
| Секреты | AndroidX Security Crypto / EncryptedSharedPreferences (DeepSeek API key) |
| Звонилка | `ROLE_DIALER` + `InCallService` |
| Запись | A (OEM import) → C (встроенный ADB / опц. Shizuku) → всегда H |
| STT | sherpa-onnx (+ модели по выбору), загрузка моделей отдельно от APK |
| AI | DeepSeek Chat API, ключ в настройках; позже свой бэкенд |
| Календарь | свой в Room + `CalendarContract` по флагу в настройках |
| Фон | WorkManager (транскрипт, анализ), уведомления о готовности |
| minSdk / targetSdk | 29 / 35 |
| Сборка | Gradle + GitHub Actions → debug/release APK артефакт |

Требования к runtime: без зависаний UI — тяжёлая работа только off-main; журнал с пагинацией; загрузка моделей и ASR не блокируют интерфейс.

## Запись разговоров

### Политика приоритетов

1. **A — OEM/штатная запись**: пресеты папок + MediaStore + ручной выбор папки; привязка файла к звонку по времени/номеру.
2. **C — привилегированный захват** (если A недоступна): **встроенный Wireless Debugging / ADB в CallMemo** (без второго приложения) + опционально внешний Shizuku.
3. **H — всегда**: диктовка после звонка, импорт голосовых/аудиофайлов вручную.

Микрофон «с динамика» как основной путь — не используем.

### Почему нельзя «скрипт автовключения» записи

Встроенную запись звонков включает **системный/OEM Phone**, не 3rd-party app. Публичного API «включи record all calls» нет:

- регион / оператор / модель могут скрывать пункт;
- настройки живут в другом приложении (Samsung Phone, MIUI Dialer, Google Phone…);
- Google Phone кладёт файлы в **private storage** (`…/com.google.android.dialer/…`) — стороннее приложение их не читает без Share/root.

Что делаем вместо «магического включения»:

1. **Автодетект вендора/модели** (`Build.MANUFACTURER`, `MODEL`, какая app — default Phone).
2. **Пресеты папок** под вендора (см. ниже) + проверка существования + MediaStore query по `DATE_ADDED` около времени звонка.
3. **Мастер первого запуска** (минимум тапов):
   - «Проверяем штатную запись…»
   - если папка есть и есть файлы → A готова;
   - если папки нет → пошаговая инструкция под модель + кнопка «Открыть настройки телефона» (deep-link / `ACTION_MAIN` в известные activity, где возможно);
   - тест: «Сделай короткий звонок с записью» → авто-поиск файла;
   - если OEM недоступен → предложить **C (встроенный ADB)** мастером «Настроить захват» (без установки второго приложения);
   - запасной вариант C: внешний Shizuku, если уже установлен;
   - в любом случае показать **H**: «Диктовать» / «Загрузить аудио».
4. **Выбор папки пользователем** (`Storage Access Framework` / tree URI) — сохраняем persistable permission.
5. Опционально: принимать **Share** аудио в CallMemo (для Google Phone «Поделиться записью»).
6. **Runtime-сканер**: после звонка искать новые audio в MediaStore (mime audio/*, имя ~ call/rec/phone, время ± окна звонка) даже если пресет не совпал — так ловим неизвестные вендоры.

Автоматически «включить запись за пользователя» скриптом **нельзя** на не-rooted устройстве. Максимум — увести в нужный экран и проверить результат.

### Целевое устройство владельца

**Tecno** (HiOS / Transsion, семейство с Infinix и itel). Типичные пути:

- `Music/PhoneRecorod` (исторический опечаточный путь на многих Tecno)
- `Music/PhoneRecord`
- `Call`
- `Recordings/Call`
- `Record/Call`

В мастере Tecno: Phone → ⚙ → Enable auto call recording (формулировка зависит от прошивки) → после первого записанного звонка автопоиск файла.

### Пресеты папок (максимально широкий стартовый каталог)

Пути относительно внутреннего хранилища. Список живёт в коде как таблица `OemRecordingPaths` + детект по `Build.MANUFACTURER` / `BRAND` / default dialer package.

| Группа | Вендоры | Типичные пути |
|--------|---------|---------------|
| Transsion | **Tecno**, Infinix, itel | `Music/PhoneRecorod`, `Music/PhoneRecord`, `Call`, `Recordings/Call`, `Record/Call` |
| Samsung | Samsung | `Recordings/Call`, `Call`, `Music/Call` |
| Xiaomi | Xiaomi, Redmi, POCO | `MIUI/sound_recorder/call_rec`, `Recordings/Call` |
| BBK | Vivo, iQOO | `Record/Call`, `VoiceRecorder/Calls`, `Call` |
| BBK/OPlus | Oppo, Realme | `Music/Recordings/Call Recordings`, `Recordings/Call Recordings`, `Record/Call` |
| OnePlus | OnePlus, ODialer | `Music/Recordings/Call Recordings`, `Record/PhoneRecord`, `Recordings/Call Recordings` |
| Huawei | Huawei, Honor | `Recordings/Call`, `Call`, `Sounds/CallRecord` |
| Motorola | Motorola, Lenovo | `CallRecordings`, `Music/CallRecordings`, `Recordings` |
| Sony | Sony | `Music/Call Recording`, `Record/Call` |
| Nokia / HMD | Nokia | `Call`, `Recordings/Call` (часто Google Phone) |
| Nothing | Nothing | часто Google Phone → private |
| ASUS | ASUS, ROG | `Recordings/Call`, `CallRecordings` |
| LG (legacy) | LG | `Voice_Recorder/CallRec`, `Call` |
| Meizu | Meizu | `Recorder/Call`, `Call` |
| ZTE / Nubia | ZTE, Nubia, RedMagic | `CallRecord`, `Recordings/Call` |
| Google / AOSP | Pixel и любой с Google Phone | **private** `com.google.android.dialer/.../callrecording` → только Share / C / H |

Дополнительно всегда:

- MediaStore query без привязки к папке;
- пользовательский tree URI;
- Share-intent receiver для аудио.

Каталог расширяем по мере тестов; неизвестный вендор → сканер + выбор папки, не «нет поддержки».

### Вариант C — можно ли «заложить Shizuku в свой APK»?

**Привилегии Shizuku в обычный APK «просто вшить» нельзя.**  
Shizuku сам по себе не даёт права: их даёт пользователь **shell** через ADB / Wireless debugging (или root). Без этого шага ни Shizuku, ни наше приложение `VOICE_CALL` не пишут.

Но **механику Shizuku можно встроить в CallMemo**, не требуя второе приложение:

| Подход | Что в APK | Действия пользователя | Отдельный Shizuku? |
|--------|-----------|------------------------|--------------------|
| **C1 — встроенный ADB (рекомендуем как основной C)** | ADB-клиент + маленький recorder-daemon (как CallVault / JemRec / skvalex Built-in ADB) | Один раз: Developer options → Wireless debugging → парный 6-значный код в нашем мастере | Нет |
| **C2 — клиент Shizuku API** | Только library `rikka.shizuku` | Установить и запустить Shizuku, выдать право CallMemo | Да |
| **C3 — root** | optional su helper | root / Magisk | Нет |

**Решение для CallMemo:** реализовать **C1 (встроенный Wireless Debugging)** как основной «про»-путь без второго приложения; **C2** — если Shizuku уже есть; **C3** — позже experimental.

Ограничения C1 (честно в UI):

- Android 11+ для Wireless debugging;
- после перезагрузки обычно нужна краткая сессия по Wi‑Fi, чтобы снова поднять daemon;
- pairing — разово, но без него система права не отдаст (это ограничение Android, не нашей лени).

Итог для пользователя Tecno: сначала A (штатная запись HiOS — часто есть) → если нет/не находит файл → мастер C1 внутри CallMemo → всегда H.

## STT (on-device)

- Движок-рантайм: **sherpa-onnx**.
- В настройках: **выпадающий список моделей** (GigaAM, Vosk small, Whisper small/turbo, streaming T-one и т.д. — список версионируем).
- Отдельный флаг: **стрим-режим** (онлайн-распознавание во время/сразу; иначе — offline после файла).
- Модель не скачана → скачивание в фоне (OkHttp/WorkManager), **progress в UI + notification**, UI не блокируем; по готовности — уведомление.
- Флаг: **авто-расшифровка после появления записи** vs **только по кнопке**.
- Язык: преимущественно RU, модели с учётом code-switch EN в русской речи (Whisper как опция).
- Дополнительно: загрузка голосовых файлов + диктовка микрофоном (путь H).

## AI (DeepSeek)

- Ключ API в настройках (зашифрованно).
- Извлекать: даты, имена, события, задачи.
- Создавать: события, напоминания, заметки (свой календарь; опционально системный).

## Календарь и аккаунт

- Свой календарь в приложении (Room).
- Интеграция с системным `CalendarContract` — выбор в настройках.
- Пока только локально на устройстве, без облачного аккаунта CallMemo.

## Доставка APK (важно для разработки с телефона)

**Участие в сборке с телефона не нужно.**

Cloud Agent / CI собирают APK на сервере:

1. Push в GitHub → GitHub Actions → артефакт `app-debug.apk` (позже signed release).
2. Ссылка на скачивание: Actions artifact или GitHub Release.
3. На телефоне: скачать → установить (разрешить установку из браузера/GitHub).

Позже, когда будет ПК: тот же Gradle локально / Android Studio. Пока достаточно «открыл ссылку → установил APK».

## Следующие шаги разработки

1. Каркас Android-проекта + CI APK.
2. Макеты UI от владельца → Compose-экраны.
3. Dialer role + журнал звонков.
4. OEM folder detector + SAF picker + import pipeline.
5. Shizuku opt-in мастер.
6. STT модель-менеджер + WorkManager транскрипт.
7. DeepSeek анализ + свой календарь / CalendarContract.
