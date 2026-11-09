# CallMemo — журнал обновлений

Подробный changelog. Краткая таблица дублируется в [STACK.md §15](STACK.md#15-история-обновлений).

**Часовой пояс дат:** Asia/Novosibirsk (UTC+7).  
**Версии:** `versionName` / `versionCode` в `app/build.gradle.kts` соответствуют последней строке журнала.

---

## 0.1.11 — In-call Telecom: accept, имя, таймер, proximity, hold/BT

| | |
|--|--|
| **Когда** | 2026-11-09 09:55 (Asia/Novosibirsk) |
| **Ветка** | `cursor/call-controls-fix-18e8` (база: `cursor/playback-dialpad-player-45fb`) |
| **versionCode** | `11` |

Пользовательские баги во время звонка:

1. **Accept не останавливал рингтон** — UI только навигировал, без `Call.answer()`.
2. **Имя = номер** — InCallService клал номер в `EXTRA_NAME` без PhoneLookup.
3. **Таймер с первой секунды набора** — Compose-таймер + stub ConnectionService `setActive()` сразу.
4. **Экран не гас у уха** — proximity был только у плеера записей.
5. **Hold / Bluetooth / mute / speaker** — локальный UI-state без Telecom.

Исправление:

- `CallSessionController` (Hilt singleton) + `@AndroidEntryPoint` `CallMemoInCallService`.
- Accept/Decline → `answer()` / `reject()`; mute/hold/speaker/BT через Telecom API.
- Имя: `callerDisplayName` + `SystemContactsDataSource.lookupDisplayName` (PhoneLookup + digit fallback).
- Таймер от `connectedAtElapsedMs` только после `STATE_ACTIVE`.
- `placeCall` выбирает SIM PhoneAccount; stub ConnectionService остаётся в `DIALING`.
- In-call proximity через `ProximityPlaybackEffect` (earpiece only).
- Hold: «На удержании» + светлая кнопка / синяя иконка; Bluetooth — sheet выбора устройства.
- Permissions: `BLUETOOTH_CONNECT` (+ legacy `BLUETOOTH` ≤30).

---

## 0.1.10 — Compositor bleed + стабильная высота меню

| | |
|--|--|
| **Когда** | 2026-11-09 09:40 (Asia/Novosibirsk) |
| **PR** | [#4](https://github.com/alekseevich47/callmemo/pull/4) `cursor/playback-dialpad-player-45fb` |
| **versionCode** | `10` |

Симптомы на Tecno:

1. Нижняя половина sticky player / dial FAB / белой шторки «просвечивала» список.
2. Экран делился горизонтальным швом: сверху «забелено», снизу резкий список.
3. Меню прыгало выше при закрытии клавиатуры (включался layout-halo).

Причина: AGSL `RuntimeShader` RenderEffect на glass frost + переключение halo ломали GPU-композитинг рядом с `hazeSource`.

Исправление:

- Убран AGSL distortion из `LiquidGlassPlate` (остался Haze blur).
- Fill плашки α≈0.72; `CompositingStrategy.Offscreen` на glass / sticky player / ChromeFab / dialpad sheet.
- Halo на bottom bar **всегда off** — Y меню как при открытой клавиатуре.
- `tabsReserve` без halo-pad.

---

## 0.1.9 — Glass menu: убрана полноширинная зона вокруг табов

| | |
|--|--|
| **Когда** | 2026-11-09 09:22 (Asia/Novosibirsk) |
| **PR** | [#4](https://github.com/alekseevich47/callmemo/pull/4) `cursor/playback-dialpad-player-45fb` |
| **versionCode** | `9` |

Причина, почему шторка «не опускалась» под меню:

1. `GlassBottomBar` сидел в хосте с **`fillMaxWidth()`** — невидимый прямоугольник на всю ширину экрана над/вокруг меню (hit-target + визуальный зазор).
2. Progressive **haze halo** (`HaloExtent ≈ 22dp`) дополнительно раздувал layout вверх/вниз от чипов, пока dialpad открыт.

Исправление:

- Хост меню — **wrap-content** по ширине чипов (центрируется через `align(BottomCenter)`), без `fillMaxWidth`.
- `halo = !dialpadMounted` — пока смонтирована шторка, halo выключен.
- `tabsReserve` считаем по высоте chip-plate (~64dp), без halo-band.

---

## Как вести этот файл

При каждом заметном PR / итерации агента:

1. Добавить секцию сверху (новее выше) или снизу в хронологическом порядке — здесь ведём **от старых к новым**.
2. Указать: версия, дата/время (NSK), PR/ветка, коммиты.
3. Коротко описать «зачем» и список конкретных изменений.
4. Синхронизировать строку в таблице STACK §15 и bump версии в Gradle при релизе.

---

## 0.0.1 — Initial commit

| | |
|--|--|
| **Когда** | 2026-11-07 22:40 (Asia/Novosibirsk) |
| **Ветка** | `main` |
| **Коммит** | `c616e97` |

Создан репозиторий `alekseevich47/callmemo` с заготовкой README.

---

## 0.1.0-docs — Документация стека и записи

| | |
|--|--|
| **Когда** | 2026-11-07 23:21–23:34 (Asia/Novosibirsk) |
| **PR** | [#1](https://github.com/alekseevich47/callmemo/pull/1) `cursor/stack-and-recording-plan-64d1` |
| **Коммиты** | `27d2365`, `4764ee2`, `26f8167` |

Зафиксированы продуктовые и технические решения до кода приложения:

- `docs/STACK.md` — стек, архитектура, политика записи A→C→H, план STT/DeepSeek.
- Расширены OEM-пресеты папок записей (Tecno/HiOS и др.), встроен план ADB-capture.
- `docs/UI.md` + карта экранов / design prompt, макеты в `docs/mockups/`.

---

## 0.1.0 — Android app shell + CI

| | |
|--|--|
| **Когда** | 2026-11-08 00:04–01:04 (Asia/Novosibirsk) |
| **PR** | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| **Коммиты** | `092c4b0`, `1faf2c8`, `d7b924e` |

Первый собираемый Android-каркас:

- Jetpack Compose + Hilt + Navigation + Room + EncryptedSharedPreferences.
- Экраны-оболочки по макету (Calls / Contacts / Calendar / Notes / Settings / In-call).
- GitHub Actions: `testDebugUnitTest` + `assembleDebug`, artifact `callmemo-debug-apk`.
- Починка SDK-пакетов CI для скачивания APK.
- Полировка журнала звонков и рабочие demo-потоки вызова.

---

## 0.1.1 — Реальные CallLog/Contacts и glass chrome

| | |
|--|--|
| **Когда** | 2026-11-08 01:37–02:11 (Asia/Novosibirsk) |
| **PR** | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| **Коммиты** | `1aa7cdd`, `4c6fce1` |

Переход с моков на системные данные и первый chrome overlay:

- `SystemCallLogDataSource` / `SystemContactsDataSource` + permissions.
- Dialpad как overlay (не NavHost destination), sticky player, swipe dismiss.
- Glass bottom bar, dial FAB, деталка звонка.
- ROLE_DIALER / InCallService / ConnectionService каркас.

---

## 0.1.2 — Playback, proximity, фильтры

| | |
|--|--|
| **Когда** | 2026-11-08 03:06–03:08 (Asia/Novosibirsk) |
| **PR** | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| **Коммиты** | `3ed2440`, `84d93ec` |

Аудио и UX плеера/набора:

- Media3 ExoPlayer + `PlaybackService` (FGS notification).
- Waveform amplitudes, compact sticky player, speed cycle.
- `ProximityPlaybackEffect` — датчик приближения → earpiece.
- Диапазонный фильтр дат на звонках, drag dialpad sheet.
- Подстройка позиции dial FAB над меню.

---

## 0.1.3 — STACK как живые правила

| | |
|--|--|
| **Когда** | 2026-11-08 04:26 (Asia/Novosibirsk) |
| **PR** | [#1](https://github.com/alekseevich47/callmemo/pull/1) |
| **Коммит** | `67b3259` |

`docs/STACK.md` переписан как обязательный living document: карта пакетов, chrome-правила, ловушки, статусы экранов — чтобы агенты не ломали контракты.

---

## 0.1.4 — Liquid glass, dialpad Calls UX, tennis date picker

| | |
|--|--|
| **Когда** | 2026-11-08 04:58–05:38 (Asia/Novosibirsk) |
| **PR** | [#2](https://github.com/alekseevich47/callmemo/pull/2) `cursor/ui-glass-dialpad-calendar-ea3f` |
| **Коммиты** | `bbd9957`, `c3e92c5`, `1b2178c` |

Крупный UI-проход:

- Haze backdrop blur + AGSL distortion (CodePen-style liquid glass), fill α≈0.25.
- Dialpad по умолчанию открыт на «Звонки»; scroll закрывает; FAB остаётся.
- Playback: async waveform, replay after end, без synthetic beep, MediaStore `ContentUris`.
- Sticky name/number + collapsing avatar на деталке.
- Tennis-style date picker modals вместо Material DatePickerDialog.
- Попытка поднять dialpad над меню через увеличение высоты sheet (частичный эффект — доработано в 0.1.7/0.1.8).

---

## 0.1.5 — Hang fix + Tecno PhoneRecord AAC

| | |
|--|--|
| **Когда** | 2026-11-08 05:52–06:20 (Asia/Novosibirsk) |
| **PR** | [#3](https://github.com/alekseevich47/callmemo/pull/3) `cursor/fix-playback-hang-recordings-ea3f` |
| **Коммиты** | `a9cb182`, `4994f52` |

Стабильность на целевом Tecno:

- UI hang: progress notification через `ACTION_REFRESH` / `notify`, **не** `startForegroundService` каждый тик.
- Жёстче matching записей (отсев WhatsApp/random Music, score по path+name).
- Tecno `Music/PhoneRecord/<номер>/*.aac`: рекурсивный обход, nested folders, MIME `AUDIO_AAC` для ExoPlayer.
- Усиление CodePen glass (blur/distort).

---

## 0.1.6 — Dialpad under tabs, glass halo, startup crash

| | |
|--|--|
| **Когда** | 2026-11-08 09:30–09:44 (Asia/Novosibirsk) |
| **PR** | [#3](https://github.com/alekseevich47/callmemo/pull/3) |
| **Коммиты** | `c6beefc`, `428404a` |

Chrome и dialpad:

- Белая шторка должна идти под меню; фиксированные ряды клавиш; scroll-dismiss на любой реальный скролл.
- Progressive haze halo вокруг glass plate.
- Call detail: быстрый CallLog-only path (запись резолвится lazy на play).
- **Crash fix:** запрет отрицательного `Modifier.padding` в halo (Compose `IllegalArgumentException` на старте); guard unbounded constraints для высоты dialpad.

---

## 0.1.7 — Pin dialpad к низу экрана

| | |
|--|--|
| **Когда** | 2026-11-09 08:41 (Asia/Novosibirsk) |
| **PR** | [#3](https://github.com/alekseevich47/callmemo/pull/3) |
| **Коммит** | `1749e7e` |

Доработка позиционирования шторки на ветке fix-playback:

- Sheet height от размеров экрана, flush к низу.
- White fill за меню; keypad/call FAB с ~108dp clearance над табами.

> Примечание: на ветке `cursor/playback-dialpad-player-45fb` (v0.1.8) принят альтернативный подход — **content-height + bottom-dock** без %-of-screen, чтобы клавиатура не «улетала» вверх.

---

## 0.1.8 — Proximity crash-safe, dialpad dock, opaque player + changelog

| | |
|--|--|
| **Когда** | 2026-11-09 09:07 (Asia/Novosibirsk) |
| **PR** | [#4](https://github.com/alekseevich47/callmemo/pull/4) `cursor/playback-dialpad-player-45fb` |
| **Коммит** | `5e0e7d2` (+ этот changelog) |
| **versionCode** | `8` |

Три пользовательских бага + документация релизов:

### Proximity / earpiece crash
- Убрана связка ручного `screenBrightness` + `PROXIMITY_SCREEN_OFF_WAKE_LOCK` (краш на Tecno/HiOS).
- Sensor callbacks на main looper; wake-lock acquire/release в `runCatching`.
- Смена маршрута: `MODE_IN_COMMUNICATION` + `setCommunicationDevice(EARPIECE)` (API 31+); `setAudioAttributes(..., handleAudioFocus=false)` при switch (focus renegotiation mid-playback ронял AudioTrack).
- Permission `MODIFY_AUDIO_SETTINGS`.

### Dialpad
- Хост: `align(BottomCenter)` + `fillMaxWidth` (**не** `fillMaxSize` — иначе тачи над шторкой блокируются).
- Высота = intrinsic content + navBars + `tabsReserve+8dp` внутри белого fill.
- Убраны %-of-screen height и `Spacer(weight)` — кнопка вызова чуть выше меню, лист белый уходит под табы.

### Sticky player
- Полностью непрозрачная плашка: `clip(shape)` + solid `CallMemoInk`.
- Vertical clearance: `tabsReserve + HaloExtent(22dp) + 12dp` — не пересекается с glass-меню.
- zIndex `1` — над списком, под dialpad (`2`).

### Документация
- Таблица обновлений в STACK §15.
- Этот файл `docs/update.md`.
- `versionName=0.1.8`, `versionCode=8`.
