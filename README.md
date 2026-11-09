# CallMemo

Android-звонилка (замена штатной) с импортом записи разговора, on-device расшифровкой, AI-анализом (DeepSeek) и напоминаниями/заметками в своём и системном календаре.

## Стек и решения

См. [docs/STACK.md](docs/STACK.md).

Кратко: Kotlin · Compose · Clean/MVVM · Room · ROLE_DIALER · OEM-запись → Shizuku → диктовка · sherpa-onnx · DeepSeek · minSdk 29.

## Сборка APK

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Также GitHub Actions → artifact `callmemo-debug-apk`. С телефона достаточно скачать и установить.

## Документация

- [docs/STACK.md](docs/STACK.md) — стек, архитектура, правила, таблица обновлений
- [docs/update.md](docs/update.md) — подробный журнал версий
- [docs/UI.md](docs/UI.md) — экраны и функционал
- [docs/mockups/callmemo-ui.png](docs/mockups/callmemo-ui.png) — макет
