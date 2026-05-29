# Project Development Workflow

This document tracks the progress of CodeStudio Mobile IDE and outlines the upcoming milestones. For
an overview of the project and its features, please see the [README.md](./README.md).

---

## 📅 Completed Progress

- **Core Editor Integration**: Successfully integrated the Sora Editor engine with support for VS
  Code extensions and syntax highlighting.
- **Initial Terminal Setup**: Established a basic Termux-based terminal environment within the app.
- **Material Design 3 UI**: Implemented a modern, dynamic user interface with theme support.
- **File System Foundation**: Built an advanced file explorer with Storage Access Framework (SAF)
  support.
- **Extension Infrastructure**: Added support for loading and managing VS Code language extensions.

---

## 🚀 Future Roadmap (What's Next)

### 1. PRoot Environment Implementation

**Status: Upcoming**
The next major step is the full implementation of the PRoot environment. This involves:

- **Environment Simulation**: Using PRoot to simulate a standard Linux filesystem structure (e.g.,
  `/data/data/com.termux`).
- **Root-Faking**: Implementing `-0` root-faking and `link2symlink` to bypass Android's filesystem
  permission restrictions.
- **Path Bindings**: Mapping internal app directories to virtual system paths to ensure
  compatibility with standard Linux binaries.

### 2. Advanced Package Management

**Status: Upcoming**
Enhancing the package management experience to make it seamless for mobile users:

- **Apt/Pkg Integration**: Creating a robust interface for handling package installations, updates,
  and removals.
- **Dependency Handling**: Automating the resolution of complex dependencies when installing
  compilers or runtimes.
- **Repository Management**: Managing Termux mirrors and ensuring stable connections for package
  downloads.

### 3. Auto-Running with Abstracted Output

**Status: Upcoming**
Simplifying the user experience by abstracting complex terminal processes:

- **One-Tap Execution**: Allowing users to run their code (e.g., Python, Node.js, C++) without
  manually typing terminal commands.
- **Abstracted Output**: Filtering and formatting terminal logs so users only see relevant
  information (success/error messages) instead of raw, intimidating shell output.
- **Background Processing**: Handling long-running tasks like compilations in the background with
  graceful progress indicators.

### 4. Final Cleanups, Checks, and Bug Fixes

**Status: Final Phase**
Ensuring the stability and reliability of the IDE:

- **Cross-Version Testing**: Verifying compatibility across different Android versions (especially
  Android 10+ restrictions).
- **Performance Optimization**: Reducing the resource footprint of the terminal and editor
  components.
- **Community Feedback & Bug Squashing**: Addressing reported issues and polishing the UI for a
  production-ready release.
- **Security Audits**: Ensuring the PRoot and shell environments are secure and respect user data
  privacy.
