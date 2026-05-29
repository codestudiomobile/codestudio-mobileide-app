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
    - Implemented a robust `TermuxPackagePatcher` that dynamically replaces package names (e.g.,
      `com.termux` to `com.cs.ide`) during installation.
    - Integrated `apt` and `dpkg` hooks to automatically patch `.deb` files and installed packages.
    - Ensured full permission compatibility for official Termux packages within the app's private
      data directory.
- **Integrated Code Execution**:
    - Implemented "One-Tap Execution" allowing users to run Python, Node.js, C++, and other
      languages directly from the editor.
    - Successfully mapped file extensions to their respective runtimes within the Termux
      environment.

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
- **Performance Optimization**: Reducing the resource footprint of the terminal and editor
  components.
- **Community Feedback & Bug Squashing**: Addressing reported issues and polishing the UI for a
  production-ready release.
- **Security Audits**: Ensuring the shell environment is secure and respects user data privacy.
