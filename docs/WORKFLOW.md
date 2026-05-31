# Project Development Workflow

This document tracks the progress of CodeStudio Mobile IDE and outlines the upcoming milestones. For
an overview of the project and its features, please see the [README.md](../README.md).

---

## 📅 Completed Progress

- **Core Editor Integration**: Successfully integrated the Sora Editor engine with support for VS
  Code extensions and syntax highlighting.
- **Initial Terminal Setup**: Established a basic Termux-based terminal environment within the app.
- **Material Design 3 UI**: Implemented a modern, dynamic user interface with theme support.
- **File System Foundation**: Built an advanced file explorer with Storage Access Framework (SAF)
  support.
- **Extension Infrastructure**: Added support for loading and managing VS Code language extensions.
- **Advanced Package Management**:
    - Implemented a robust `TermuxPackagePatcher` and `TermuxPatcher` byte-replacement engine.
    - Standardized string translations matching exactly **10 characters** (from `com.termux` to
      `com.csmide`), successfully updating pre-compiled ELF binary absolute paths without shifting
      string offsets, section layouts, or structure boundaries.
    - Integrated `apt` and `dpkg` hooks to automatically intercept, extract (`dpkg-deb -R`), patch,
      permission-chmod (`0755` via Os.chmod), and repack (`dpkg-deb -b`) debian packages.
- **Interactive Code Execution**:
    - Implemented "One-Tap Execution" to copy external SAF files into isolated private storage (
      `bin_exec_cache`) and compile/execute them using resolved build command configurations (
      `commands.json`).
    - Successfully mapped file extensions to their respective runtimes within the Termux
      environment.
- **Terminal Environment Personalization**:
    - Integrated a visual configuration controller **`CustomizationActivity.java`** featuring
      instant prompts preview and alphanumeric ASCII art block text generators.
    - Deployed custom shell scripts `apply-title.sh` and `apply-banner.sh` executing dynamically
      inside `/bin/bash` processes to inject environment customizers.
- **High-Performance Core Orchestration**:
    - Refactored `MainActivity` into a multi-threaded architecture using `ExecutorService` thread
      pools to offload heavy I/O operations from the UI thread.
    - Optimized Storage Access Framework (SAF) interactions with direct `DocumentsContract` cursor
      queries, ensuring 60 FPS responsiveness during rapid file navigation and large-scale saving
      operations.
    - Implemented a zero-block UI strategy for the Sora Editor, offloading text-to-byte
      conversions and disk writes to background executors to prevent ANRs on large files.
- **Enhanced Asset & UI Logic**:
    - Standardized 28 symbol and navigation icons into a unified 24x24 Vector Drawable engine for
      the ExtraKeys toolbar.
    - Optimized `SoraLanguageManager` with asynchronous TextMate grammar initialization and VS Code
      extension loading.
    - Built a robust HTML/Web execution path using temporary external cache directories and
      `StrictMode` bypasses for universal browser compatibility.
    - Refined the "Save As" logic to pre-fill current file names and perform smart change
      validation before committing to disk.
- **PTY/NDK Native Emulation Layer**:
    - Deployed JNI Pseudo-Terminal bindings (`termux.c` NDK code) managing multiplexer forks (
      `/dev/ptmx`), POSIX slave redirects, and dynamic column/row resizing using Linux system
      calls (`ioctl(TIOCSWINSZ)`).

---

## 🚀 Future Roadmap (What's Next)

### 1. Creating Abstraction Layers

**Status: CURRENT FOCUS**
Moving towards a more user-friendly experience by abstracting the complexities of the terminal:

- **Abstracted Output Engine**: Developing a layer to filter and format terminal logs, presenting
  users with clean "Success" or "Error" notifications instead of raw shell output.
- **Graphical Process Management**: Visualizing background tasks (like long compilations) with
  progress bars and status indicators outside the raw terminal view.
- **Input Abstraction**: Providing simplified UI prompts for scripts that require user input, rather
  than relying solely on the interactive terminal.

### 2. Final Cleanups, Checks, and Bug Fixes

**Status: Final Phase**
Ensuring the stability and reliability of the IDE:

- **Cross-Version Testing**: Verifying compatibility across different Android versions (especially
  Android 10+ restrictions).
- **Community Feedback & Bug Squashing**: Addressing reported issues and polishing the UI for a
  production-ready release.
- **Security Audits**: Ensuring the shell environment is secure and respects user data privacy.
