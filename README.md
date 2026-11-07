# CallMemo

Android-звонилка (замена штатной) с импортом записи разговора, on-device расшифровкой, AI-анализом (DeepSeek) и напоминаниями/заметками в своём и системном календаре.

## Стек и решения

См. [docs/STACK.md](docs/STACK.md).

Кратко: Kotlin · Compose · Clean/MVVM · Room · ROLE_DIALER · OEM-запись → Shizuku → диктовка · sherpa-onnx · DeepSeek · minSdk 29.

## Сборка APK

Собирается в CI / облаке. Для установки на телефон достаточно скачать готовый APK — локальная сборка с телефона не нужна.
