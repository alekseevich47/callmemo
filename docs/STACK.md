# CallMemo — стек, архитектура и правила проекта

> **Живой файл-правила.** Обновлять при каждом заметном изменении архитектуры, экранов, зависимостей или контрактов.  
> Цель: не забывать, **что уже есть**, **где лежит** и **как применяется**.  
> UI-спека / макеты: [UI.md](UI.md). Макеты: `docs/mockups/`.

**Последнее обновление:** 2026-11-09 09:55 (Asia/Novosibirsk) — v0.1.11 (in-call Telecom: answer, name, timer, proximity, hold/BT).  
**Changelog:** краткая таблица — [§15](#15-история-обновлений); подробно — [update.md](update.md).

---

## 1. Продукт

Android-звонилка (замена штатной) + прослушивание записей + (план) on-device STT + DeepSeek + свой календарь/заметки.

| | |
|--|--|
| Package / applicationId | `com.alekseevich47.callmemo` (+ `.debug` в debug) |
| App name | CallMemo |
| minSdk / targetSdk / compileSdk | 29 / 35 / 35 |
| versionName / versionCode | `0.1.11` / `11` |
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
  DialerRoleHelper.kt          # ROLE_DIALER, PhoneAccount, placeCall (SIM account)
  TelecomServices.kt           # InCallService (@AndroidEntryPoint), ConnectionService stub
  CallSessionController.kt     # StateFlow session: answer/hold/mute/audio + contact name

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
│  DialerRoleHelper / CallSessionController ← InCallService    │
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

- Компонент: `LiquidGlassPlate` (`ui/components/LiquidGlass.kt`).
- **Haze**: `hazeSource` на контенте (`NavHost`), `hazeEffect` на плашке — backdrop blur под меню.
- **Без AGSL RenderEffect** — на Tecno/HiOS distortion ломал композитинг низа экрана (горизонтальный шов + bleed списка сквозь FAB/player/dialpad).
- Плашка: белый fill **α≈0.72** + blur ~22dp; `CompositingStrategy.Offscreen`.
- Halo (progressive) для bottom bar **всегда off** — иначе прыгает Y меню при open/close dialpad.
- Применять ко всем будущим chrome-плашкам с glass.

### Glass bottom bar

- **Одна** плашка по ширине чипов (`LiquidGlassPlate`) — хост **wrap-content**, **никогда** `fillMaxWidth`.
- **Halo всегда false** — стабильная высота/Y (как при открытой клавиатуре на скрине).
- Активный таб: только цвет иконки/текста (синий), без заливки чипа.
- Ripple со **скруглёнными** углами (`clip` + `ripple`).
- Плашка **поверх** списка и **поверх dialpad** (zIndex tabs `4` > dialpad `2`).
- При уходе на settings/detail хранить `lastTopLevel` — иначе NPE в exit `AnimatedVisibility`.
- Info на списке звонков — **иконка без круглой обводки**.

### Sticky player

- Компактная тёмная плашка; play слева от волны; скорость x1→x1.3→x1.5→x2.
- **Opaque:** `CompositingStrategy.Offscreen` + `clip` + `CallMemoInk` — список не просвечивает (в т.ч. нижняя половина).
- **Высота:** `bottom = tabsReserve + 12dp` (+ `navigationBarsPadding`).
- zIndex `1` — над списком, **под** dialpad (`2`); tabs остаются `4`.
- Волна грузится **в фоне** после старта ExoPlayer (не блокирует playback).
- Свайп в сторону; порог **≥ 50%**; после dismiss **не** `snapTo(0)` + `ExitTransition.None` — без flash.
- **Показывать** sticky только **вне** деталки того звонка, который сейчас играет.
- После `STATE_ENDED` повторный play → seek(0) + play (`ended` flag).
- Нет записи → **не** играть synthetic beeps; bar без звука / `playing=false`.
- MediaStore URI только через `ContentUris.withAppendedId` (не `withAppendedPath`).
- Progress в notification: `ACTION_REFRESH` / `NotificationManager.notify` (~2 с), **не** `startForegroundService` каждый тик (это вешало UI).
- Waveform: лёгкий `MediaExtractor` sample-size preview — **без** MediaCodec PCM decode.

### Dialpad sheet

- На разделе **Звонки** открывается сразу; **любой успешный** скролл списка (не overscroll) — плавно закрывает; FAB **всегда** видна в закрытом состоянии.
- Хост шторки: `align(BottomCenter)` + `fillMaxWidth` (**не** `fillMaxSize` — иначе тачи над шторкой блокируются). Белая панель **докнута к низу** и рисуется **под** glass-меню.
- Высота = **intrinsic content** (grabber + номер + 4×52dp + call FAB) + `navigationBars` + `bottomContentPadding` (`tabsReserve+8dp`). **Не** %-of-screen и **без** `Spacer(weight)` — иначе клавиатура «улетает» вверх.
- Клавиши: фиксированная высота ряда ~52dp / hit 56dp (не weight) — кнопка вызова чуть выше меню.
- Sheet + call FAB: `CompositingStrategy.Offscreen` (анти-bleed на Tecno).
- Dismiss ≥ **30%**; уход с Calls → unmount; возврат → open; dismiss работает и mid-enter.

### FAB

- Компонент: `ChromeFab` — круглая тень сразу, elevation M3 = 0, `CompositingStrategy.Offscreen`.
- Константа: `ChromeFabBottomPadding = 90.dp` + `navigationBarsPadding`.
- FAB клавиатуры на Calls **не** прячется скроллом списка (скрывается только dialpad).

### Переходы разделов

- `NavHost` enter/exit/pop: короткий fade + лёгкий horizontal slide (~160ms).

### Date picker (tennis)

- `CallMemoDatePickerModal` / `CallMemoDateRangePickerModal` — модалка поверх экрана как в tennis (membership / DatePickerModal): days→months→years, swipe периода, hint, «Подтвердить».
- Фильтр дат на Звонках и выбор даты события в Календаре — через эти модалки (не Material DatePickerDialog).

### Proximity

- Пока идёт воспроизведение (`playing`): поднесение к уху → `PROXIMITY_SCREEN_OFF_WAKE_LOCK` + маршрут **earpiece**.
- Sensor callbacks → **main looper**; wake-lock acquire/release в `runCatching`; **без** ручного `screenBrightness` (на Tecno/HiOS связка brightness + proximity lock крашит процесс).
- Смена маршрута: `AudioManager.MODE_IN_COMMUNICATION` + `setCommunicationDevice(EARPIECE)` (API 31+); `ExoPlayer.setAudioAttributes(..., handleAudioFocus=false)` при switch — focus renegotiation mid-playback крашит AudioTrack.
- Manifest: `MODIFY_AUDIO_SETTINGS` + `WAKE_LOCK`.
- Отвели — `MODE_NORMAL` + `clearCommunicationDevice`.

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
- `callDetail(id)` — быстрый CallLog only (entry + history); запись резолвится **lazy на play**
- `observeNotes` / `addNote`
- `observeCalendar` / `observeCalendarDay` / `addCalendarItem`
- `settings` / `updateSettings` / `maskedApiKey`

### System CallLog

- ContentObserver на `CallLog.Calls`.
- Лимит ~300 записей; поиск по имени/номеру; фильтр дат (включительно по дню).
- History по номеру: `PhoneNumberUtils.sameSubscriber` (хвост цифр ≥ 7).

### Recording

- **A (сделано):** `RecordingLocator` — MediaStore (Audio + Files) + recursive OEM walk.
- **Tecno/HiOS:** `Music/PhoneRecord/<номер>/*.aac` — цифры в **папке**, рекурсивный обход, soft nearest ≤48ч.
- Prefer `content://` URI; `.aac` → ExoPlayer `MimeTypes.AUDIO_AAC`.
- Matching: `scoreCandidate` по path+name (цифры в path считаются). WhatsApp/random Music — reject.
- Если файла нет → **не** synthesise beep; `playing=false`. Resolve на play через locator.
- **C / H:** план (см. §10).

### Playback controller API

```
play(callId, contactName, number, uri?, durationHintMs, startedAtEpochMs)
toggle() / seekTo() / cycleSpeed() / setEarpiece() / hide()
state: PlaybackUiState(visible, callId, name, number, uri, playing,
                       positionMs, durationMs, speed, amplitudes, earpiece)
```

### Telecom / In-call

- `DialerRoleHelper.isDefaultDialer()` / `createRequestIntent()` / `registerPhoneAccount()` / `placeCall()`.
- `placeCall` явно выбирает **SIM PhoneAccount** (не stub CallMemo ConnectionService).
- `CallMemoInCallService` (`@AndroidEntryPoint`) → `CallSessionController` (singleton StateFlow).
- `CallSessionController`: `answer` / `reject` / `disconnect` / `setMuted` / `hold|unhold` / audio route (API 29–33: `setAudioRoute`/`requestBluetoothAudio`; API 34+: `CallEndpoint`) / DTMF.
- Имя абонента: `Call.Details.callerDisplayName` + `SystemContactsDataSource.lookupDisplayName` (PhoneLookup).
- Таймер: `connectedAtElapsedMs` только после `Call.STATE_ACTIVE` (не при DIALING).
- Proximity на in-call: тот же `ProximityPlaybackEffect` (wake-lock), пока маршрут earpiece и не speaker/BT.
- Hold UI: под статусом «⏸ На удержании»; кнопка светлая, иконка синяя.
- Bluetooth: bottom sheet выбора endpoint/устройства.
- Stub `CallMemoConnectionService` **не** делает outgoing `setActive()` сразу.

---

## 8. Экраны — что работает

| Экран | Статус |
|-------|--------|
| Звонки + поиск + фильтр дат | ✅; dialpad open-by-default; scroll hides sheet |
| Dialpad overlay | ✅; bottom-docked under wrap-width menu (no halo band); call FAB above tabs |
| Деталка: sticky имя/номер, collapsing avatar, history, inline play | ✅ |
| Sticky player + notification + proximity | ✅; Offscreen opaque; above tabs; crash-safe proximity |
| Контакты (системные) | ✅; FAB «добавить» — stub (`ChromeFab`) |
| Календарь месяц + события Room | ✅; дата события — tennis DatePickerModal |
| Заметки Room | ✅ |
| Настройки: dialer role, API key, флаги STT/source | ✅ persist; crash fix при уходе с табов |
| In-call / Incoming UI | ✅ Telecom-привязка (answer/hold/mute/speaker/BT/proximity/timer/name); Stream — placeholder |
| Тестовый входящий из настроек | ✅ UI-демо без Telecom; live-call идёт через InCallService |

---

## 9. Разрешения и компоненты (Manifest)

**Permissions:** INTERNET, READ/WRITE_CALL_LOG, READ_CONTACTS, CALL_PHONE, READ_PHONE_STATE, MANAGE_OWN_CALLS, RECORD_AUDIO, FOREGROUND_SERVICE (+ MICROPHONE, MEDIA_PLAYBACK), POST_NOTIFICATIONS, READ/WRITE_CALENDAR, READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE(≤32), WAKE_LOCK, MODIFY_AUDIO_SETTINGS, BLUETOOTH(≤30), BLUETOOTH_CONNECT.

**Components:**

- `MainActivity` — LAUNCHER, DIAL/VIEW `tel`, CALL_BUTTON  
- `CallMemoInCallService` — `BIND_INCALL_SERVICE`, `IN_CALL_SERVICE_UI`  
- `CallMemoConnectionService` — `BIND_TELECOM_CONNECTION_SERVICE`  
- `PlaybackService` — FGS `mediaPlayback`, not exported  

---

## 10. Политика записи (продуктовая, A → C → H)

Согласовано в чате; **в коде сейчас только поиск A** (без synthetic tone fallback).

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
6. FAB: только через `ChromeFab` / `ChromeFabBottomPadding=90.dp`; при видимом player — `playerClearance + 64.dp`.  
7. Длительность плеера = media duration / duration звонка; **не** хардкодить 20 с.  
8. Room call_logs/contacts — не писать «в журнал» из InCall вместо системного CallLog, пока нет явной стратегии кэша.  
9. Glass — fill α≈0.72 + blur 22dp; **no AGSL RenderEffect**; halo **always off** on bottom bar; host never `fillMaxWidth`.  
10. Dialpad: **content-height + bottom-dock** + Offscreen layer; dismiss **30%**; Calls → open; scroll → close.  
11. Не использовать `topLevel!!` в chrome во время exit-анимации — только `lastTopLevel`.  
12. Date pickers — tennis-модалки из `DatePickerModals.kt`, не Material DatePickerDialog.  
13. Playback: `ContentUris.withAppendedId`; waveform async; no synthetic beep if file missing; ended → replay.  
14. Sticky player / ChromeFab: `CompositingStrategy.Offscreen` + opaque fill (Tecno bleed).  
15. Call detail: sticky name/number; avatar scale/alpha collapse on scroll.  
16. Proximity: no `screenBrightness` + proximity wake-lock; earpiece switch without audio-focus renegotiation.  
17. Menu Y must not depend on dialpad open/closed (no layout halo toggle).
18. Incoming Accept **must** call `Call.answer()` (via `CallSessionController`) — иначе рингтон не останавливается.  
19. Outgoing `placeCall` — только SIM PhoneAccount; stub ConnectionService не `setActive()` на dialing.  
20. In-call timer только после `STATE_ACTIVE`; кнопки mute/hold/speaker/BT — через Telecom, не локальный fake-state (кроме Settings demo).

---

## 14. Как обновлять этот файл

При каждом PR / итерации агента, если менялось:

- зависимость / minSdk / сервис / permission → §2, §9  
- пакет / класс / поток данных → §3, §4, §7  
- chrome / FAB / player / dialpad → §6  
- статус фичи → §8, §10, §11  
- ловушка → §13  
- релиз / заметный фикс → §15 + [update.md](update.md); bump `versionName`/`versionCode` в `app/build.gradle.kts`

В начале файла обновить строку **«Последнее обновление»** (дата/время Asia/Novosibirsk + версия).  
Не дублировать длинные UI-промпты — они в [UI.md](UI.md).

---

## 15. История обновлений

Краткая лента. Время — **Asia/Novosibirsk (UTC+7)**. Подробности — [update.md](update.md).

| Дата и время | Версия | Изменения (кратко) | PR / ветка |
|--------------|--------|--------------------|------------|
| 2026-11-07 22:40 | `0.0.1` | Initial commit репозитория | `main` |
| 2026-11-07 23:21 | `0.1.0-docs` | STACK, OEM-пресеты записей, UI-карта/промпт | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| 2026-11-08 00:04 | `0.1.0` | Android shell из макета + CI APK artifact | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| 2026-11-08 01:37 | `0.1.1` | Реальные CallLog/Contacts, glass chrome, dialpad overlay | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| 2026-11-08 03:06 | `0.1.2` | Playback/waveform/proximity, фильтры дат, drag dialpad | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| 2026-11-08 04:26 | `0.1.3` | STACK.md → живые правила архитектуры | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| 2026-11-08 04:58 | `0.1.4` | Haze/AGSL glass, dialpad Calls UX, tennis date picker, sticky detail | [#2](https://github.com/alekseevich47/callmemo/pull/2) |
| 2026-11-08 05:52 | `0.1.5` | UI hang fix, Tecno PhoneRecord AAC, без false recording matches | [#3](https://github.com/alekseevich47/callmemo/pull/3) |
| 2026-11-08 09:30 | `0.1.6` | Dialpad under tabs, scroll-dismiss, glass halo, no negative padding crash | [#3](https://github.com/alekseevich47/callmemo/pull/3) |
| 2026-11-09 08:41 | `0.1.7` | Pin dialpad к низу экрана, clearance FAB над меню | [#3](https://github.com/alekseevich47/callmemo/pull/3) |
| 2026-11-09 09:07 | `0.1.8` | Proximity crash-safe; dialpad content-dock; opaque sticky player above tabs; changelog | [#4](https://github.com/alekseevich47/callmemo/pull/4) |
| 2026-11-09 09:22 | `0.1.9` | Glass menu: убран full-width host + halo-band над dialpad | [#4](https://github.com/alekseevich47/callmemo/pull/4) |
| 2026-11-09 09:40 | `0.1.10` | Compositor bleed fix (no AGSL; Offscreen chrome); меню без прыжка Y | [#4](https://github.com/alekseevich47/callmemo/pull/4) |
| 2026-11-09 09:55 | `0.1.11` | In-call: answer, имя контакта, таймер после ACTIVE, proximity, hold/BT/mute | `cursor/call-controls-fix-18e8` |
