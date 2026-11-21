# Coding-board

Standalone coding keyboard for Android, written in Java.

## Use

Copy the `keyboard` module into your Android project and add it to `settings.gradle`:

```gradle
include ':keyboard'
```

Then add the dependency and attach the keyboard to an editor:

```gradle
implementation project(':keyboard')
```

```java
CodingBoardKeyboardView keyboard = new CodingBoardKeyboardView(context);
root.addView(keyboard, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM));
keyboard.setTargetView(editor);
```

Show `CodingBoardKeyboardSettingsView` from the `onSettingsRequested()` callback, or provide only
the settings your app needs. Use `setInputConnection()` for custom editors. Pass an optional
preference name to `CodingBoardKeyboardView(context, "editor_keyboard")` to isolate its settings.

## Features

- Ctrl, Alt, Shift, Meta, Fn, Esc, Tab, Enter, navigation and function keys.
- Cut, copy, paste, select all, clipboard history, emoji, voice input and layout switching.
- Docked, floating and split modes, plus per-key colors and appearance controls.
- Optional word movement, word selection and word deletion shortcuts, each with its own toggle.

## Demo and compatibility

The `demo` app includes a code editor and the full settings panel. Its default layout is English
QWERTY, its preferences are isolated from other app versions, and key sound is disabled.
Minimum Android API: 23. Java source/target compatibility: 11. Build with JDK 17 and Android SDK 35.
The ready-to-install debug APK is `demo/Coding-board-demo.apk`.

```sh
./gradlew :keyboard:assembleRelease
./gradlew :demo:assembleDebug
```

## License

Apps that distribute this keyboard must comply with GPL-3.0 and the attribution terms in `LICENSE`.
The upstream license is included in `LICENSE-UNEXPECTED-KEYBOARD`.
