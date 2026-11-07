# CallMemo — стек, архитектура и правила проекта

> **Живой файл-правила.** Обновлять при каждом заметном изменении архитектуры, экранов, зависимостей или контрактов.  
> Цель: не забывать, **что уже есть**, **где лежит** и **как применяется**.  
> UI-спека / макеты: [UI.md](UI.md). Макеты: `docs/mockups/`.

**Последнее обновление:** 2026-11-07 (после sticky player / dialpad / real CallLog / default dialer / notification).

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
  components/CommonUi.kt       # glass bar, sticky player, SearchField, FAB pad
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

Тесты: `app/src/test/...` — `OemRecordingPathsTest`, `PhoneNumberUtilsTest`, `TimeFormatTest`.

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

### Glass bottom bar

- Одна полупрозрачная белая **плашка** под чипами (frosted: alpha ~0.58 + blur wash на API 31+).
- Активный таб: только цвет иконки/текста (синий), без заливки чипа.
- Ripple со **скруглёнными** углами (`clip` + `ripple`).
- Плашка **поверх** списка (контент виден под ней / скроллится под chrome).

### Sticky player

- Компактная тёмная плашка; play слева от волны; скорость x1→x1.3→x1.5→x2.
- Волна = **реальные амплитуды** (`WaveformAnalyzer`), progress = позиция воспроизведения.
- Свайп в сторону с анимацией; порог закрытия **≥ 50%** ширины, иначе snap back.
- **Показывать** sticky только **вне** деталки того звонка, который сейчас играет.  
  На деталке того же `callId` — только inline-плеер в карточке; sticky скрыт.
- При уходе с этой деталки sticky снова появляется (если `visible`).
- При открытой клавиатуре: dialpad **поверх** player (zIndex 3 > 1), воспроизведение **не** останавливать; при закрытии шторки player снова виден под ней.

### Dialpad sheet

- Opaque half-sheet (~58% высоты, min ~420dp), не fullscreen.
- Grabber сверху; тянуть вниз — живой offset; dismiss при **≥ 50%** высоты, иначе назад.
- Backspace **справа в поле номера**, только если есть цифры.
- Закрывать при смене любого таба.
- Bottom padding шторки = место под **табы** (player не резервировать — клавиатура поверх).

### FAB

- Константа: `ChromeFabBottomPadding = 90.dp` — чуть ниже исторического ~96.dp, **не** у меню.
- FAB клавиатуры / контакта / события / заметки — **один уровень** (`bottom = ChromeFabBottomPadding`).
- С включённым sticky player FAB клавиатуры **выше** плашки (`90 + 58`).
- На Calls: при скролле вниз FAB плавно скрывается, вверх — появляется; при старте видна.
- При переходе на Calls FAB и chrome табов появляются через общий fade (не раньше чужих FAB).

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
- `callDetail(id)` — entry + history + попытка `RecordingLocator`
- `observeNotes` / `addNote`
- `observeCalendar` / `observeCalendarDay` / `addCalendarItem`
- `settings` / `updateSettings` / `maskedApiKey`

### System CallLog

- ContentObserver на `CallLog.Calls`.
- Лимит ~300 записей; поиск по имени/номеру; фильтр дат (включительно по дню).
- History по номеру: `PhoneNumberUtils.sameSubscriber` (хвост цифр ≥ 7).

### Recording

- **A (сделано):** `RecordingLocator` — MediaStore в окне времени звонка + скан OEM-папок из `OemRecordingPaths`.
- Если файла нет → синтетический WAV на **длительность звонка** (не фиксированные 20 с) — чтобы UI/звук не ломались.
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
| Звонки + поиск + фильтр дат | ✅ системный CallLog |
| Dialpad overlay | ✅ |
| Деталка: история (3 + expand), SMS/Ещё/share, inline play | ✅ |
| Sticky player + notification + proximity | ✅ |
| Контакты (системные) | ✅; FAB «добавить» — stub |
| Календарь месяц + события Room | ✅ ручное создание |
| Заметки Room | ✅ |
| Настройки: dialer role, API key, флаги STT/source | ✅ persist; download/STT/DeepSeek — нет |
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
6. FAB: `90.dp` база; не прижимать к меню (~80 и ниже).  
7. Длительность плеера = media duration / duration звонка; **не** хардкодить 20 с.  
8. Room call_logs/contacts — не писать «в журнал» из InCall вместо системного CallLog, пока нет явной стратегии кэша.

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
