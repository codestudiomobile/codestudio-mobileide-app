# CodeStudio Mobile IDE

CodeStudio is a powerful, open-source integrated development environment (IDE) for Android. We
believe that a smooth development experience is essential, even on mobile. To achieve this,
CodeStudio deeply integrates the robust Termux ecosystem directly into the IDE, harmonizing a
professional editor with a powerful Linux environment. It's not just a wrapper—it's a synchronized
workspace where your code and terminal breathe together.

## 🚀 Features

- **Professional Editor**: Powered by the Sora Editor engine, supporting syntax highlighting,
  auto-completion, and multi-language support via VS Code extensions.
- **Integrated Terminal**: A full-featured terminal powered by Termux, providing a robust Linux
  environment. It automatically synchronizes with the editor, opening at your current working
  directory for a truly integrated experience.
- **Package Management**: Built-in `apt` and `pkg` management for installing compilers, runtimes,
  and utilities (Python, Node.js, C++, etc.).
- **Modern UI**: Material Design 3 interface with dynamic themes and customizable layouts. The
  interface is highly optimized for performance, ensuring smooth transitions and responsiveness even
  under heavy file operations.
- **File Management**: Advanced file explorer with support for Android Storage Access Framework (
  SAF). Features a multi-threaded architecture for high-speed file discovery, loading, and saving
  without UI lag.
- **Workspace Support**: Organize your projects into workspaces for better productivity.

## 📁 Project Structure

For an exhaustive, deep-dive breakdown of the entire system architecture (including JNI, PTY
management, NDK layers, and packaging pipelines),
see [code_studio_architecture_analysis.md](./docs/code_studio_architecture_analysis.md).

Additional developer resources:

- [COMPONENTS.md](./docs/COMPONENTS.md): Quick component-level testing and manual verification
  guide.
- [WORKFLOW.md](./docs/WORKFLOW.md): Development timeline, progress tracking, and abstraction
  roadmap.

Core code locations:

- `app/src/main/java/com/cs/ide/app`: Core IDE logic, text editors, fragments, and activities (
  including `CustomizationActivity.java` and `MainActivity.java`).
- `app/src/main/java/com/cs/ide/termux`: Terminal rendering, view, and Linux environment bindings.
- `app/src/main/cpp`: NDK Pseudo-Terminal (PTY) system forks, socket channels, and bootstrap
  loading.

## 🛠️ Customization

CodeStudio features a robust console personalization engine. You can dynamically update your
interactive terminal greetings (ASCII banners) and prompt titles visually via the built-in *
*`CustomizationActivity`** (accessible from Settings) or directly in-shell using `apply-banner` and
`apply-title` CLI commands.

For step-by-step styling instructions, see [CUSTOMIZATION.md](./docs/CUSTOMIZATION.md).

## 🚧 Development Status

CodeStudio is currently under active development. For a detailed roadmap of past progress and
upcoming milestones, please see our [WORKFLOW.md](./docs/WORKFLOW.md).

- **Package Management**: Fully functional with automatic package name patching and `apt`
  integration.
- **Code Execution**: Stable "One-Tap" execution for multiple languages directly from the editor.
- **Current Focus**: Creating **Abstraction Layers** to provide a cleaner, non-intimidating
  interface for terminal-based processes.

Experimental features:

- **LSP Support**: Integration of Language Server Protocol is in the early stages.

## 📥 Download

Ready to start coding? Grab the latest version of CodeStudio from the releases page:

[![Download APK](https://img.shields.io/badge/Download-APK-success?style=for-the-badge&logo=android)](../../releases)

*(Or check the [Releases](../../releases) section for older versions and changelogs.)*

## 🤝 Contributing

We love contributions! Whether you're fixing a bug, adding a new language feature, or improving the
documentation, your help makes CodeStudio better for everyone.

### How to get involved:

- **🌟 Star the repo**: Show your support!
- **🐛 Report Bugs**: Open an issue if something isn't working.
- **💡 Feature Requests**: Have an idea? We'd love to hear it.
- **🛠️ Pull Requests**: Send us your code! We appreciate clean, well-documented changes.

Join our community and help build the future of mobile development!

## 📄 License

This project is licensed under the [MIT License](LICENSE).
