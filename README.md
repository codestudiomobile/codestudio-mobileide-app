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
- **Modern UI**: Material Design 3 interface with dynamic themes and customizable layouts.
- **File Management**: Advanced file explorer with support for Android Storage Access Framework (
  SAF).
- **Workspace Support**: Organize your projects into workspaces for better productivity.

## 📁 Project Structure

For a detailed breakdown of the internal components, see [COMPONENTS.md](./docs/COMPONENTS.md). To
track our development roadmap and progress, see [WORKFLOW.md](./WORKFLOW.md).

- `app/src/main/java/com/cs/ide/app`: Core IDE logic, activities, and fragments.
- `app/src/main/java/com/cs/ide/termux`: Terminal and Linux environment integration.
- `app/src/main/assets/vscode_extensions`: Pre-bundled language support files.

## 🛠️ Customization

CodeStudio is highly customizable. You can personalize your terminal experience with custom banners
and prompt titles.
See [CUSTOMIZATION.md](./docs/CUSTOMIZATION.md) for instructions.

## 🚧 Development Status

CodeStudio is currently under active development. For a detailed roadmap of past progress and
upcoming milestones, please see our [WORKFLOW.md](./WORKFLOW.md).

Some modules may be experimental:

- **Language Packs**: Automated installation of certain language runtimes is currently being
  refined.
- **LSP Support**: Integration of Language Server Protocol is in the early stages.
- **PRoot Environment**: The PRoot integration is under active development. You may encounter issues
  with certain system paths or specialized commands, and the terminal experience may not yet be
  fully graceful.

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
