# 🛠️ CodeStudio Component Architecture & Testing Guide

Welcome to the deep dive of CodeStudio! This document is designed to give you a hands-on
understanding of every moving part in this IDE. Whether you're a developer or a curious user, you
can use this guide to manually test and verify each component.

---

## 1. The Command Center: `MainActivity` & `TabManager`

**What it is:** The heart of CodeStudio. It manages the lifecycle of the app, the sidebar
navigation, and the multi-tab editing experience.

### 🔍 Technical Highlights:

- **Multi-Tab Engine**: Uses `ViewPager2` to swap between open files seamlessly.
- **Drawer Navigation**: Integrated with Android's Storage Access Framework (SAF) to browse your
  local device and cloud storage.

### 🕹️ How to Test it Yourself:

1. **Launch**: Open CodeStudio. You should see the `MainActivity` initialize.
2. **Sidebar**: Swipe from the left edge. Browse through your `/sdcard` or project folders.
3. **Multi-Tasking**: Open multiple files (e.g., a `.java` and a `.py`). Tap the tabs at the top to
   switch between them. Verify that the state (cursor position) is maintained.

---

## 2. The Professional Editor: `TextFragment` (Sora Editor)

**What it is:** A high-performance code editor capable of handling large files with syntax
highlighting and smart features.

### 🔍 Technical Highlights:

- **Engine**: Powered by `Sora Editor`.
- **Theming**: Supports VS Code `.tmTheme` files.
- **Syntax**: Uses TextMate grammars (`.tmLanguage.json`) pre-bundled in `assets/vscode_extensions`.

### 🕹️ How to Test it Yourself:

1. **Syntax Check**: Open a `.js` or `.php` file. Verify that keywords are colored correctly based
   on the language.
2. **Shortcuts**: Use the "Extra Keys" bar above the keyboard (Tab, Ctrl, Arrows).
3. **Auto-Save**: Make a change, close the tab, and reopen it. Your changes should be there!

---

## 3. The Powerhouse: `TerminalFragment` & `TermuxService`

**What it is:** A fully integrated Linux terminal that lives right inside your IDE.

### 🔍 Technical Highlights:

- **Sync Technology**: The terminal automatically `cd`s into the directory of the file you are
  currently editing.
- **Persistence**: `TermuxService` keeps your terminal session alive even if you switch apps to look
  up a tutorial.

### 🕹️ How to Test it Yourself:

1. **Sync Test**: Open a file in a specific folder. Swipe to the terminal tab. Run `pwd`. It should
   match the folder of your file!
2. **Interactive Shell**: Run `top` or `htop`. Use `Ctrl+C` from the extra keys to stop it.
3. **Persistence**: Open a terminal, run a long command (like `ping google.com`), switch to Chrome,
   then come back. The terminal should still be running.

---

## 4. Smart Execution: `ExecutionManager` & `TermuxRunner`

**What it is:** The bridge between your code and the terminal. It knows how to run your files based
on their extension.

### 🔍 Technical Highlights:

- **Language Detection**: Automatically maps `.py` to `python`, `.js` to `node`, etc.
- **Command Injection**: Sends the correct run command directly to the active terminal session.

### 🕹️ How to Test it Yourself:

1. **Python Run**: Create a `test.py` with `print("Hello CodeStudio")`.
2. **The "Play" Button**: Tap the Play icon in the top toolbar.
3. **Verify**: The IDE should automatically switch to the terminal and you should see
   `python test.py` executed with the output visible.

---

## 5. Package Management: `AptBackgroundService`

**What it is:** Handles the installation of compilers and tools without freezing the UI.

### 🔍 Technical Highlights:

- **Notification Updates**: Shows real-time installation progress in the Android notification tray.
- **UI Integration**: `ManageLanguagesActivity` provides a "Store-like" experience for installing
  runtimes.

### 🕹️ How to Test it Yourself:

1. **Go to Settings**: Navigate to "Manage Languages".
2. **Install a Tool**: Try installing `clang` or `nodejs`.
3. **Monitor**: Pull down your notification bar. You should see the `apt` progress. Once finished,
   try running `node -v` in the terminal to verify.

---

## 6. Workspace Initialization & Customization

**What it is:** Ensures the local environment is correctly set up on the first launch and allows
users to make the IDE their own.

### 🕹️ How to Test it Yourself:

1. **Banners**: Go to `CUSTOMIZATION.md` and follow the steps to change the terminal header.
2. **Themes**: Change the app theme in Settings. Notice how the Material 3 "Dynamic Color" adapts to
   your wallpaper (on Android 12+).

---

> **Tip for Developers**: Most of the core logic resides in `app/src/main/java/com/cs/ide/app`. If
> you want to add a new language, check out `SoraLanguageManager.java`.
