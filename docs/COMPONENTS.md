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

**What it is:** Handles the installation, patching, and binary translation of Linux packages from
official repositories. **This component is fully functional.**

### 🔍 Technical Highlights:

- **Dynamic Binary Translation**: Standard Termux binaries hardcode search paths referring to
  `/data/data/com.termux`. Because `com.termux` and Code Studio's package name `com.cs.ide` are both
  exactly **10 characters** long, the `TermuxPackagePatcher` performs a safe, direct byte-level
  string replacement (`System.arraycopy`) on raw compiled ELF files and shell scripts. This patches
  paths without altering file sizes, string offsets, or ELF memory headers.
- **Debian Re-Packaging Hooks**: Intercepts `apt-get` downloads natively via pre-install and
  post-invoke streams. It extracts `.deb` packages using `dpkg-deb -R`, renames internal folders
  containing `com.termux`, modifies file content strings, restores maintainer scripts executable
  permissions (`chmod 0755` via POSIX `android.system.Os.chmod` system calls), and repacks the
  patched tree back to `.deb` using `dpkg-deb -b`.
- **Background Processing**: `AptBackgroundService` handles long-running downloads and installations
  in a foreground service, sending real-time notifications with speed and stage parameters.

---

## 6. Workspace Initialization & Customization

**What it is:** Ensures the local Linux environment directory structures, home mount points, and
symlinks are set up on launch, and exposes graphical dashboards for personalization.

### 🔍 Technical Highlights:

- **Visual Personalization (`CustomizationActivity.java`)**: A dedicated configuration layout
  featuring real-time input fields and high-fidelity previews. As you type, the visual banner
  preview renders block letter ASCII art dynamically by stitching row coordinates from an internal
  Java `asciiMap`.
- **Dynamic CLI Injections**: Tapping Apply saves variables and executes `apply-banner.sh` and
  `apply-title.sh` inside the Termux virtualized `/bin/bash` shell via `ProcessBuilder`, modifying
  prompt titles (`PS1` and `PROMPT_COMMAND` in `.bashrc`) and greeting banners across all open
  terminal sessions immediately.

---

For a comprehensive, native-level architectural and NDK systems deep-dive, see
the [Comprehensive Architecture Analysis Document](./code_studio_architecture_analysis.md).

## 🚀 Next Step: Abstraction Layers

We are currently building **Abstraction Layers** to hide the raw terminal output. The goal is to
provide a graphical interface for process management and clean "Success/Failure" messages, making
the IDE more accessible while keeping the terminal available for power users.

---

> **Tip for Developers**: Most of the core logic resides in `app/src/main/java/com/cs/ide/app`. If
> you want to add a new language, check out `SoraLanguageManager.java`.
