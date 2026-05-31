# Getting Started with CodeStudio

This guide covers the initial setup and system requirements for CodeStudio Mobile IDE.

## 📱 System Requirements

To ensure a smooth development experience, your device should meet the following requirements:

- **Android Version**: Android 8.0 (API 26) or higher.
- **Architecture**: ARM64 (AArch64) is highly recommended. x86_64, ARMv7, and x86 are also
  supported.
- **Storage**: At least **1.5GB** of free space for installation, compilers, and project files.
- **Memory**: 4GB RAM or higher recommended.

## 💾 Storage Setup

CodeStudio requires access to your device's shared storage to manage your project files effectively.

### 1. Granting Permissions

Upon first launch, CodeStudio will request storage permissions. Please **Allow** this to ensure you
can open and edit files from your device's internal storage (e.g., Downloads, Documents).

### 2. Accessing Shared Storage

Once permission is granted, your shared storage is automatically linked. You can access it in the
terminal at:

```bash
~/storage/shared
```

### 3. Internal App Storage

For tasks requiring maximum performance (like compilation), you can also use the internal app
storage (`$HOME`).

## 📦 Basic Usage

- **Update Packages**: Run `pkg update` in the terminal after installation.
- **Install Tools**: Use `pkg install <tool_name>` (e.g., `pkg install python`).
- **Run Code**: Use the "Run" icon in the editor for supported languages.

## ⚠️ Troubleshooting

- **Permission Denied**: If you can't access `/sdcard`, make sure storage permission is enabled in
  Android Settings for CodeStudio.
- **Command Not Found**: Ensure you have installed the required package via `pkg install`.
