# CallMemo — стек, архитектура и правила проекта

> **Живой файл-правила.** Обновлять при каждом заметном изменении архитектуры, экранов, зависимостей или контрактов.  
> Цель: не забывать, **что уже есть**, **где лежит** и **как применяется**.  
> UI-спека / макеты: [UI.md](UI.md). Макеты: `docs/mockups/`.

**Последнее обновление:** 2026-11-07 (fix hang + broken recordings matching).

---

## 1. Продукт

Android-звонилка (замена штатной) + прослушивание записей + (план) on-device STT + DeepSeek + свой календарь/заметки.

| | |
|--|--|
| Package / applicationId | `com.alekseevich47.callmemo` (+ `.debug` в debug) |
| App name | CallMemo |
| minSdk / targetSdk / compileSdk | 29 / 35 / 35 |
| versionName | `0.1.0` |
| Дистрибуция сейчас | sideload APK из GitHub Actions |
| Целевое устройство владельца | Tecno (HiOS / Transsion) |

---

## 2. Технологический стек (фактический)

| Слой | Выбор в коде |
|------|----------------|
| Язык / UI | Kotlin, Jetpack Compose, Material 3 |
| Архитектура | Clean-ish + MVVM (UI → ViewModel → Repository → data sources) |
| DI | Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`) |
| Async | Coroutines + Flow / StateFlow |
| Навигация | Navigation Compose |
| Локальные данные | **Room** — заметки и календарь; системный **CallLog / Contacts** для звонков и контактов |
| Настройки / секреты | **EncryptedSharedPreferences** (`SecureSettingsStore`); зависимость DataStore есть, **не используется** |
| Звонилка | `ROLE_DIALER` + `InCallService` + `ConnectionService` |
| Плеер | Media3 ExoPlayer + FGS `PlaybackService` |
| Permissions UI | Accompanist permissions |
| Backdrop glass | **Haze** `dev.chrisbanes.haze:haze` + AGSL distortion (API 33+) |
| Сборка | Gradle 8.x, JDK 17, KSP |
| CI | `.github/workflows/android.yml` → artifact `callmemo-debug-apk` |

**Не подключено в код (только план / настройки-заглушки):** sherpa-onnx, WorkManager, OkHttp download моделей, DeepSeek HTTP-клиент, встроенный ADB/Shizuku recorder, SAF picker папки записей, Share-receiver аудио.

---

## 3. Карта пакетов

Корень: `app/src/main/java/com/alekseevich47/callmemo/`

```
CallMemoApp.kt                 # @HiltAndroidApp
MainActivity.kt                # intents → CallMemoAppRoot
di/AppModule.kt                # Room DB + SecureSettingsStore

domain/model/Models.kt         # доменные типы

data/
  repository/CallMemoRepository.kt
  local/                       # Room: Entities, Daos, Mappers, CallMemoDatabase
  system/                      # SystemCallLogDataSource, SystemContactsDataSource
  recording/                   # OemRecordingPaths, RecordingLocator
  secure/SecureSettingsStore.kt
  SeedData.kt                  # НЕ используется (legacy mock)

telecom/
  DialerRoleHelper.kt          # ROLE_DIALER, PhoneAccount, placeCall
  TelecomServices.kt           # InCallService, ConnectionService, CallSessionTracker

player/
  CallPlaybackController.kt    # singleton ExoPlayer + state
  PlaybackService.kt           # foreground notification
  WaveformAnalyzer.kt          # амплитуды из файла
  ProximityPlaybackEffect.kt   # датчик приближения → earpiece + screen off

ui/
  CallMemoAppRoot.kt           # NavHost + chrome (tabs / player / dialpad / FAB)
  navigation/NavDestinations.kt
  components/
    CommonUi.kt                # glass bar, sticky player, SearchField, SectionCard
    LiquidGlass.kt             # LiquidGlassPlate — reusable glass (CodePen qErQmXO)
    ChromeFab.kt               # shared FAB: circular shadow + navBars + pad
    DatePickerModals.kt        # tennis-style single/range date modals
  calls/                       # Calls, CallDetail, Dialpad, ViewModels
  contacts/ ContactsScreen.kt
  calendar/ CalendarScreen.kt
  notes/ NotesScreen.kt
  settings/ SettingsScreen.kt
  incall/ InCallScreens.kt
  player/ PlayerViewModel.kt
  theme/ Theme.kt              # Manrope, light palette

util/ PhoneNumberUtils.kt, TimeFormat.kt
```

Тесты: `app/src/test/...` — `OemRecordingPathsTest`, `RecordingLocatorScoreTest`, `PhoneNumberUtilsTest`, `TimeFormatTest`.

---

## 4. Архитектура и потоки данных

```
┌─────────────────────────────────────────────────────────────┐
│  Compose UI (screens) + CallMemoAppRoot chrome               │
│       │                                                      │
│       ▼                                                      │
│  Hilt ViewModels                                             │
│       │                                                      │
│       ▼                                                      │
│  CallMemoRepository                                          │
│    ├─ SystemCallLogDataSource  ← CallLog.Calls               │
│    ├─ SystemContactsDataSource ← ContactsContract            │
│    ├─ Room NoteDao / CalendarDao                             │
│    ├─ SecureSettingsStore                                    │
│    └─ RecordingLocator (+ OemRecordingPaths)                 │
│                                                              │
│  PlayerViewModel → CallPlaybackController (singleton)        │
│       ├─ ExoPlayer / WaveformAnalyzer                        │
│       ├─ PlaybackService (notification)                      │
│       └─ ProximityPlaybackEffect (из UI при playing)         │
│                                                              │
│  DialerRoleHelper / CallSessionTracker ← Telecom             │
└─────────────────────────────────────────────────────────────┘
```

### Правила слоёв

1. **Журнал звонков и контакты** — только из системных провайдеров (не Room).  
2. **Room `call_logs` / `contacts`** — сущности и DAO есть, **репозиторий их не использует** (мертвый код до миграции/кэша).  
3. **Заметки и календарь** — только Room.  
4. **Плеер** — глобальный singleton; не привязан к жизненному циклу одного экрана.  
5. **SeedData** — не вызывать; реальных моков в runtime нет.

---

## 5. Навигация

### Табы (`TopLevelDestination`)

| Route | Экран |
|-------|--------|
| `calls` | Журнал |
| `contacts` | Контакты |
| `calendar` | Календарь |
| `notes` | Заметки |

### Вторичные (`Routes`)

| Route | Назначение |
|-------|------------|
| `call_detail/{callId}` | Деталка звонка |
| `settings` | Настройки |
| `in_call/{name}/{number}/{direction}` | Активный вызов |
| `incoming/{name}/{number}` | Входящий |
| `dialpad` | **Константа есть, но dialpad — overlay**, не destination NavHost |

Старт: `calls`. Intents `tel:` / `ACTION_DIAL` → открывают dialpad sheet с номером.  
Telecom → `MainActivity.ACTION_SHOW_INCOMING` / `SHOW_IN_CALL` / `OPEN_CALL_DETAIL`.

---

## 6. UI chrome — обязательные правила

Код: `ui/CallMemoAppRoot.kt`, `ui/components/CommonUi.kt`.

### Liquid glass (переиспользуемый)

- Компонент: `LiquidGlassPlate` (`ui/components/LiquidGlass.kt`), референс CodePen `giladfeder/qErQmXO`.
- **Haze**: `hazeSource` на контенте (`NavHost`), `hazeEffect` на плашке — реальный backdrop blur текста под меню.
- **Distortion** (API 33+): AGSL RuntimeShader (turbulence/displacement) на frost-слое; иконки/текст чипов остаются crisp сверху.
- Fill alpha **~0.36** (−10% от прежних ~0.46); soft shadow + specular rim + highlight.
- Применять ко всем будущим chrome-плашкам с glass; не дублировать полупрозрачный белый фон без Haze.

### Glass bottom bar

- **Одна** плашка по ширине чипов (`LiquidGlassPlate`) — без full-width underlay.
- Активный таб: только цвет иконки/текста (синий), без заливки чипа.
- Ripple со **скруглёнными** углами (`clip` + `ripple`).
- Плашка **поверх** списка и **поверх dialpad** (zIndex tabs `4` > dialpad `2`).
- При уходе на settings/detail хранить `lastTopLevel` — иначе NPE в exit `AnimatedVisibility`.
- Info на списке звонков — **иконка без круглой обводки**.

### Sticky player

- Компактная тёмная плашка; play слева от волны; скорость x1→x1.3→x1.5→x2.
- Волна грузится **в фоне** после старта ExoPlayer (не блокирует playback).
- Свайп в сторону; порог **≥ 50%**; после dismiss **не** `snapTo(0)` + `ExitTransition.None` — без flash.
- **Показывать** sticky только **вне** деталки того звонка, который сейчас играет.
- После `STATE_ENDED` повторный play → seek(0) + play (`ended` flag).
- Нет записи → **не** играть synthetic beeps; bar без звука / `playing=false`.
- MediaStore URI только через `ContentUris.withAppendedId` (не `withAppendedPath`).
- Progress в notification: `ACTION_REFRESH` / `NotificationManager.notify` (~2 с), **не** `startForegroundService` каждый тик (это вешало UI).
- Waveform: лёгкий `MediaExtractor` sample-size preview — **без** MediaCodec PCM decode.

### Dialpad sheet

- На разделе **Звонки** открывается сразу; скролл списка вниз — плавно уезжает вниз; FAB клавиатуры **всегда** видна в закрытом состоянии.
- Opaque half-sheet (~62% высоты, min ~460dp — чуть выше прежних 58%/420dp); enter/exit slide **внутри** sheet.
- Dismiss при **≥ 30%** высоты на отпускании, иначе snap back.
- Кнопка вызова не пересекается с меню за счёт **высоты шторки** (не подъёма над меню); bottom pad = tabs.
- Номер по центру над клавишами; backspace справа.
- Закрывать при уходе с Calls; при возврате на Calls — снова открыть.

### FAB

- Компонент: `ChromeFab` — круглая тень сразу, elevation M3 = 0.
- Константа: `ChromeFabBottomPadding = 90.dp` + `navigationBarsPadding`.
- FAB клавиатуры на Calls **не** прячется скроллом списка (скрывается только dialpad).

### Переходы разделов

- `NavHost` enter/exit/pop: короткий fade + лёгкий horizontal slide (~160ms).

### Date picker (tennis)

- `CallMemoDatePickerModal` / `CallMemoDateRangePickerModal` — модалка поверх экрана как в tennis (membership / DatePickerModal): days→months→years, swipe периода, hint, «Подтвердить».
- Фильтр дат на Звонках и выбор даты события в Календаре — через эти модалки (не Material DatePickerDialog).

### Proximity

- Пока идёт воспроизведение (`playing`): поднесение к уху → `PROXIMITY_SCREEN_OFF_WAKE_LOCK` + маршрут **earpiece** (`USAGE_VOICE_COMMUNICATION`).
- Отвели — обычный media-маршрут и яркость.

### Уведомление воспроизведения

- `PlaybackService` (FGS mediaPlayback), layout `res/layout/notification_playback.xml`.
- Аватар/иконка, имя, номер, seek bar, время elapsed/total, play/pause по центру, **AI ✦** → деталка.
- Свайп уведомления / deleteIntent → `controller.hide()` (стоп).

---

## 7. Ключевые классы и контракты

### Repository

`CallMemoRepository`:

- `observeCalls(query, fromEpochMs?, toEpochMs?)`
- `observeContacts(query)`
- `callDetail(id)` — entry + history; `RecordingLocator` только для **открытого** entry (не N× scan по history)
- `observeNotes` / `addNote`
- `observeCalendar` / `observeCalendarDay` / `addCalendarItem`
- `settings` / `updateSettings` / `maskedApiKey`

### System CallLog

- ContentObserver на `CallLog.Calls`.
- Лимит ~300 записей; поиск по имени/номеру; фильтр дат (включительно по дню).
- History по номеру: `PhoneNumberUtils.sameSubscriber` (хвост цифр ≥ 7).

### Recording

- **A (сделано):** `RecordingLocator` — MediaStore в окне времени звонка + скан OEM-папок из `OemRecordingPaths`.
- Matching **строгий**: `scoreCandidate >= 2` (call name/path и/или цифры номера). Случайная музыка / WhatsApp в том же окне — **не** запись.
- Если файла нет → **не** synthesise beep; `playing=false`, bar без звука. Resolve на play через locator.
- **C / H:** план (см. §10).

### Playback controller API

```
play(callId, contactName, number, uri?, durationHintMs, startedAtEpochMs)
toggle() / seekTo() / cycleSpeed() / setEarpiece() / hide()
state: PlaybackUiState(visible, callId, name, number, uri, playing,
                       positionMs, durationMs, speed, amplitudes, earpiece)
```

### Telecom

- `DialerRoleHelper.isDefaultDialer()` / `createRequestIntent()` / `registerPhoneAccount()` / `placeCall()`.
- `CallMemoInCallService` поднимает UI; `CallSessionTracker` для answer/reject/disconnect.
- In-call кнопки mute/speaker/hold… пока **локальный UI-state**, не полная привязка к Telecom API.

---

## 8. Экраны — что работает

| Экран | Статус |
|-------|--------|
| Звонки + поиск + фильтр дат | ✅; dialpad open-by-default; scroll hides sheet |
| Dialpad overlay | ✅; swipe ≥30%; taller sheet (~62%/460dp); FAB when closed |
| Деталка: sticky имя/номер, collapsing avatar, history, inline play | ✅ |
| Sticky player + notification + proximity | ✅; replay after end; no beep fallback |
| Контакты (системные) | ✅; FAB «добавить» — stub (`ChromeFab`) |
| Календарь месяц + события Room | ✅; дата события — tennis DatePickerModal |
| Заметки Room | ✅ |
| Настройки: dialer role, API key, флаги STT/source | ✅ persist; crash fix при уходе с табов |
| In-call / Incoming UI | ✅ визуал; Stream — placeholder |
| Тестовый входящий из настроек | ✅ только UI-навигация |

---

## 9. Разрешения и компоненты (Manifest)

**Permissions:** INTERNET, READ/WRITE_CALL_LOG, READ_CONTACTS, CALL_PHONE, READ_PHONE_STATE, MANAGE_OWN_CALLS, RECORD_AUDIO, FOREGROUND_SERVICE (+ MICROPHONE, MEDIA_PLAYBACK), POST_NOTIFICATIONS, READ/WRITE_CALENDAR, READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE(≤32), WAKE_LOCK.

**Components:**

- `MainActivity` — LAUNCHER, DIAL/VIEW `tel`, CALL_BUTTON  
- `CallMemoInCallService` — `BIND_INCALL_SERVICE`, `IN_CALL_SERVICE_UI`  
- `CallMemoConnectionService` — `BIND_TELECOM_CONNECTION_SERVICE`  
- `PlaybackService` — FGS `mediaPlayback`, not exported  

---

## 10. Политика записи (продуктовая, A → C → H)

Согласовано в чате; **в коде сейчас только поиск A + tone fallback**.

1. **A — OEM/штатная запись** — пресеты папок + MediaStore + (план) SAF tree URI.  
2. **C — привилегированный захват** — C1 встроенный Wireless Debugging/ADB (основной план), C2 Shizuku, C3 root.  
3. **H — всегда** — диктовка / импорт файла / Share.

Автоматически «включить системную запись» скриптом нельзя. Мастер первого запуска — в плане.

Пресеты вендоров: `OemRecordingPaths` (Tecno/Infinix/itel, Samsung, Xiaomi, Vivo, Oppo/Realme, OnePlus, Huawei/Honor, …).

---

## 11. План: STT / AI / фон (ещё не в коде)

| Тема | Решение |
|------|---------|
| STT runtime | sherpa-onnx; модели в настройках (`SttModelId`) |
| Стрим | флаг `streamModeEnabled` |
| Авто-расшифровка | флаг `autoTranscribe` |
| Скачивание моделей | фон + progress + notification (WorkManager / OkHttp) |
| AI | DeepSeek Chat API; ключ уже в EncryptedSharedPreferences |
| Результат AI | события / напоминания / заметки в свой календарь; опц. CalendarContract |
| Фон | WorkManager: транскрипт + анализ |

---

## 12. CI и APK

```
push main | cursor/**  +  pull_request
  → testDebugUnitTest
  → assembleDebug
  → artifact name: callmemo-debug-apk
    path: app/build/outputs/apk/debug/*.apk
```

Локально: `./gradlew assembleDebug`.

---

## 13. Ловушки (не сломать при правках)

1. Не возвращать **SeedData** / `ensureSeeded` в runtime.  
2. Не путать DataStore-зависимость с реальным store настроек (EncryptedSharedPreferences).  
3. Не навигировать на `Routes.DIALPAD` — использовать overlay state.  
4. Sticky player ↔ деталка: скрывать sticky только для **того же** `callId`.  
5. Dialpad поверх player без `hide()` плеера.  
6. FAB: только через `ChromeFab` / `ChromeFabBottomPadding=90.dp`; не прижимать к меню.  
7. Длительность плеера = media duration / duration звонка; **не** хардкодить 20 с.  
8. Room call_logs/contacts — не писать «в журнал» из InCall вместо системного CallLog, пока нет явной стратегии кэша.  
9. Glass — `LiquidGlassPlate` + Haze source/effect + AGSL distort; fill α≈0.36.  
10. Dialpad dismiss: порог **30%**; Calls → open; scroll → close; FAB always when closed.  
11. Не использовать `topLevel!!` в chrome во время exit-анимации — только `lastTopLevel`.  
12. Date pickers — tennis-модалки из `DatePickerModals.kt`, не Material DatePickerDialog.  
13. Playback: `ContentUris.withAppendedId`; waveform async; no synthetic beep if file missing; ended → replay.  
14. Sticky player swipe dismiss: no `snapTo(0)` + `ExitTransition.None`.  
15. Call detail: sticky name/number; avatar scale/alpha collapse on scroll.

---

## 14. Как обновлять этот файл

При каждом PR / итерации агента, если менялось:

- зависимость / minSdk / сервис / permission → §2, §9  
- пакет / класс / поток данных → §3, §4, §7  
- chrome / FAB / player / dialpad → §6  
- статус фичи → §8, §10, §11  
- ловушка → §13  

В начале файла обновить строку **«Последнее обновление»**.  
Не дублировать длинные UI-промпты — они в [UI.md](UI.md).
