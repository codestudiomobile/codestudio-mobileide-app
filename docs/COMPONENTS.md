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

---

## 2. The Professional Editor: `TextFragment` (Sora Editor)

**What it is:** A high-performance code editor capable of handling large files with syntax
highlighting and smart features.

### 🔍 Technical Highlights:

- **Engine**: Powered by `Sora Editor`.
- **Theming**: Supports VS Code `.tmTheme` files.
- **Syntax**: Uses TextMate grammars (`.tmLanguage.json`) pre-bundled in `assets/vscode_extensions`.

---

## 3. The Powerhouse: `TerminalFragment` & `TermuxService`

**What it is:** A fully integrated Linux terminal that lives right inside your IDE.

### 🔍 Technical Highlights:

- **Sync Technology**: The terminal automatically `cd`s into the directory of the file you are
  currently editing.
- **Persistence**: `TermuxService` keeps your terminal session alive even if you switch apps to look
  up a tutorial.

---

## 4. Smart Execution: `ExecutionManager` & `TermuxRunner`

**What it is:** The bridge between your code and the terminal. It knows how to run your files based
on their extension. **This component is fully functional.**

### 🔍 Technical Highlights:

- **One-Tap Execution**: Implements "One-Tap" logic that detects file extensions (Python, Node.js,
  C++, etc.) and executes the appropriate runtime in the synchronized terminal.
- **Environment Awareness**: Ensures that the terminal environment is fully initialized with correct
  PATHs before execution.

---

## 5. Package Management: `AptBackgroundService` & `TermuxPackagePatcher`

**What it is:** Handles the installation and patching of Linux packages. **This component is fully
functional.**

### 🔍 Technical Highlights:

- **Dynamic Patching**: Uses `TermuxPackagePatcher` to swap hardcoded paths (replacing `com.termux`
  with `com.cs.ide`) inside binary and script files during installation.
- **APT/DPkg Hooks**: Intercepts package installations via pre-install and post-invoke hooks to
  ensure 100% compatibility with official Termux repositories.
- **Background Processing**: `AptBackgroundService` handles long-running downloads and installations
  with real-time notification updates.

---

## 6. Workspace Initialization & Customization

**What it is:** Ensures the local environment is correctly set up on the first launch and allows
users to make the IDE their own.

---

## 🚀 Next Step: Abstraction Layers

We are currently building **Abstraction Layers** to hide the raw terminal output. The goal is to
provide a graphical interface for process management and clean "Success/Failure" messages, making
the IDE more accessible while keeping the terminal available for power users.

---

> **Tip for Developers**: Most of the core logic resides in `app/src/main/java/com/cs/ide/app`. If
> you want to add a new language, check out `SoraLanguageManager.java`.
