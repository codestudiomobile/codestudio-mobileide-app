# Getting Started with CodeStudio

This guide covers the initial setup, system requirements, and essential configuration steps for CodeStudio Mobile IDE.

## 📱 System Requirements

To ensure a smooth development experience, your device should meet the following minimum requirements:

- **Android Version**: Android 8.0 (API 26) or higher.
- **Architecture**: ARM64 (AArch64) is highly recommended for best performance and package compatibility. x86_64, ARMv7, and x86 are also supported.
- **Storage**: 
    - At least **500MB** of free space for the initial installation and bootstrap.
    - Additional space (1GB+ recommended) for compilers, runtimes, and project files.
- **Memory**: 4GB RAM or higher recommended for running modern compilers and LSP services.

## 💾 Storage Configuration

CodeStudio uses a hybrid storage model to balance security and convenience.

### 1. Internal App Storage (`$HOME`)
- **Location**: `/data/data/com.csmide/files/home`
- **Use Case**: This is where your Linux home directory resides. It's the primary location for configuration files, local project clones, and where the `apt` package manager installs software.
- **Performance**: Fastest performance for file operations and compilation.

### 2. Android Shared Storage (SD Card/Internal)
- **Setup**: To access your device's shared storage (e.g., your Downloads or Documents folder), run the following command in the integrated terminal:
  ```bash
  termux-setup-storage
  ```
- **Access**: Once granted, your shared storage will be accessible via the `~/storage/shared` symlink.
- **Note**: Due to Android's Scoped Storage restrictions, certain execution permissions are restricted on shared storage. We recommend keeping your source code in the Internal App Storage for full feature support (like running scripts).

## 📦 Package Management

CodeStudio comes with a pre-configured package repository.

- **Updating Repositories**: Before installing new software, always update the package lists:
  ```bash
  pkg update
  ```
- **Installing Packages**: You can install compilers and tools directly:
  ```bash
  pkg install python nodejs clang git
  ```
- **One-Tap Execution**: For many languages, clicking the "Run" icon in the editor will automatically handle the execution via the background service.

## 🔧 Environment Synchronization

CodeStudio automatically patches its environment to match your unique installation:

- **Package Identity**: All internal paths are automatically updated from `com.termux` to `com.csmide`.
- **Bash Integration**: Your `~/.bashrc` is pre-configured to synchronize the editor's current folder with the terminal's working directory.

## ⚠️ Troubleshooting

- **"Command not found"**: Ensure you have run `pkg update` and that the required package is installed.
- **Permission Denied**: If you encounter permission issues on `/sdcard`, ensure you have run `termux-setup-storage`.
- **Performance Lag**: Large projects or complex LSP services may require significant RAM. Close unused tabs or background apps if performance degrades.
