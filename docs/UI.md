# CallMemo — UI и функционал (черновик)

Статус: зафиксировано 2026-11-07. Макеты — по промпту ниже / от пользователя. Позже дорабатываем.

## Навигация и оболочка

- Иконка приложения: **трубка** (phone).
- Старт: сразу **главная** (не splash/онбординг как обязательный экран).
- Нижняя навигация (4 раздела): **отдельные компактные glass-капсулы** (как Telegram), blur/прозрачность; активный таб подсвечивает только иконку и текст (без заливки кнопки).
  1. Звонки
  2. Контакты
  3. Календарь
  4. Заметки
- Сверху на главных списках: **поиск** + вход в **настройки**.
- В журнале: время (`сегодня` / `Вчера, HH:mm` / `d MMM, HH:mm`) + кнопка **i** → деталка; тап по контакту → звонок.
- Настройки → **Тестовый звонок** (входящий → активный → журнал).

## Экраны

### 1. Звонки (главная)
- Список прошлых звонков (журнал).
- Набор номера (dialpad) — доступ с этого раздела.
- Тап по звонку / номеру → **исходящий вызов**.
- Сбоку / в detail-pane (или push-экран): **карточка абонента** —
  - история звонков с этим номером;
  - расшифровка текущего/выбранного;
  - прослушивание записи.
- **Sticky-плеер** с фоновым воспроизведением записи (сворачивается, не теряется при навигации).

### 2. Экран активного звонка (InCall)
- Аватар, имя, номер.
- Типовые кнопки default dialer: mute, keypad, speaker, hold, add call, bluetooth… (набор уточним).
- Отдельная кнопка **«Стрим»** (стрим-режим расшифровки / live STT — связано с настройкой стрима).

### 3. Входящий звонок
- Accept / Decline / **Ответить сообщением** (quick reply).

### 4. Контакты
- Список / поиск / карточка контакта → звонок, история, заметки (детали позже).

### 5. Календарь
- Сначала **месячная сетка** с точками на днях, где есть события/напоминания/задачи.
- Тап по дате → список дня (как agenda на макете: время, название, напоминание).
- Свой календарь в приложении + события/напоминания из AI.
- Связь с системным календарём — флаг в настройках.
- Макет-референс: `docs/mockups/callmemo-ui.png`.

### 6. Заметки
- Заметки по звонкам / ручные / из AI-извлечения.

### 7. Настройки (не в таббаре, из шестерёнки)
- DeepSeek API key.
- Выбор STT-модели (download + progress).
- Флаг стрим-режима.
- Авто-расшифровка vs по кнопке.
- Источник записи: OEM-папка / встроенный ADB / Shizuku / только диктовка.
- Выбор папки записей, системный календарь on/off.
- Импорт аудио / диктовка как fallback.

## Сквозной функционал (не экраны)

- Полная роль default dialer.
- A → C1 (in-app ADB) → H запись.
- Фон: транскрипт, AI-анализ, создание событий/напоминаний/заметок.
- Sticky audio player + background playback.

## Промпт для генерации дизайна

См. блок в ответе пользователю / раздел ниже — актуальная короткая версия.

```
Design an Android app "CallMemo" — modern replacement phone dialer + call intelligence.
Style: refined liquid glass bottom tab bar; soft depth, blur, subtle translucency; not generic purple gradient, not cream-serif terracotta, not dark neon glow. Clear brand-forward phone/dialer identity. Expressive typography (not Inter/Roboto). Cohesive light-first palette with one strong accent. Mobile-first, Material-ish but custom.
Screens (generate all):
1) Calls home: recent call log, top search + settings gear, dialpad access, liquid-glass bottom tabs: Calls | Contacts | Calendar | Notes. Tapping a number places a call.
2) Call detail (side panel or secondary screen): subscriber history, transcript of selected call, audio scrubber; sticky mini-player for background playback.
3) In-call UI: avatar, name, number, standard dialer controls (mute, keypad, speaker, hold, add, bluetooth) + prominent "Stream" button for live transcription.
4) Incoming call: accept, decline, reply with message.
5) Contacts list, Calendar (events/reminders from calls), Notes list — same chrome/tabs.
6) Settings (from gear): API key, STT model picker, stream toggle, auto-transcribe, recording source.
App icon concept: telephone handset.
Keep first viewport of Calls uncluttered: search, log, dial action, glass tabs — no promo cards or stat strips.
Output: high-fidelity mobile mockups for each screen, consistent components, Russian UI labels.
```
