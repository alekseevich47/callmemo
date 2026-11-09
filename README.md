# CallMemo

Android-звонилка (замена штатной) с импортом записи разговора, on-device расшифровкой, AI-анализом (DeepSeek) и напоминаниями/заметками в своём и системном календаре.

## Клонирование на ПК

```bash
git clone https://github.com/alekseevich47/callmemo.git
cd callmemo
```

Открой папку в **Android Studio** → *Open* → выбери корень `callmemo` (где `settings.gradle.kts`).  
Дождись Gradle Sync.

## Что нужно в Android Studio (SDK Manager)

| Оставить | Не нужно |
|----------|----------|
| JDK **17** (Embedded JDK) | NDK / CMake |
| Android SDK Platform **API 35** | Эмуляторные System Images |
| Build-Tools **35.x**, Platform-Tools | |
| Command-line Tools | |

## Сборка APK

В терминале Android Studio / ПК:

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Установка на телефон:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Или: скопировать APK на устройство и открыть.

Также: GitHub Actions → artifact `callmemo-debug-apk`.

## Стек и решения

См. [docs/STACK.md](docs/STACK.md).

Кратко: Kotlin · Compose · Clean/MVVM · Room · ROLE_DIALER · OEM-запись → Shizuku → диктовка · sherpa-onnx · DeepSeek · minSdk 29.

## Документация

- [docs/STACK.md](docs/STACK.md) — стек, архитектура, правила, таблица обновлений
- [docs/update.md](docs/update.md) — подробный журнал версий
- [docs/UI.md](docs/UI.md) — экраны и функционал
- [docs/mockups/callmemo-ui.png](docs/mockups/callmemo-ui.png) — макет
