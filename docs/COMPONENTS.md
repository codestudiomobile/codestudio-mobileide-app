# Project Components

This document provides a technical overview of the key components that make up the CodeStudio Mobile
IDE.

## 1. Core IDE Components (`com.cs.ide.app`)

### MainActivity

The central hub of the application. It manages the multi-tab interface, the file explorer, and
coordinates between the editor and terminal fragments.

- **Tab Management**: Uses `ViewPager2` and a custom `TabManager` to handle multiple open files.
- **File Explorer**: Provides a side-drawer navigation for workspace files using the Storage Access
  Framework.

### TextFragment (The Editor)

The primary code editing component.

- **Editor Engine**: Powered by `io.github.rosemoe.sora.widget.CodeEditor`.
- **Language Support**: Integrated with `SoraLanguageManager` which loads TextMate grammars and VS
  Code themes.
- **Features**: Auto-save, undo/redo, search/replace, and extra key shortcuts.

### ExecutionManager

Handles the execution of source files. It detects the language of the file and routes the execution
to the appropriate compiler or interpreter within the terminal environment.

### SoraLanguageManager

Handles language-specific configurations. It maps file extensions to appropriate syntax highlighters
and loads language packs from assets or external storage.

## 2. Terminal & Shell (`com.cs.ide.termux`)

### TerminalFragment

Provides the shell interface. It communicates with a backend `TermuxService` to manage terminal
sessions.

- **Environment**: Automatically initializes a PRoot environment to provide a standard Linux
  filesystem structure.
- **Interoperability**: Shares environment variables like `OPENED_FOLDER` with the IDE to keep the
  terminal and editor in sync. The terminal automatically opens in the currently active folder in
  the editor, providing a seamless integrated development experience.
- **Status**: *Under Development*. The PRoot implementation is still being refined. Users might not
  experience the full terminal experience gracefully, as some path mappings and system calls are
  currently being optimized.

### TermuxService

A foreground service that maintains active terminal sessions even when the app is in the background.

## 3. Background Services

### AptBackgroundService

A dedicated service for managing package installations.

- **Operation**: Executes `pkg` and `apt` commands.
- **Feedback**: Provides real-time progress updates via a system notification and broadcasts events
  to the UI.
- **Status**: *Under Development*. Improvements to error handling and dependency resolution are
  ongoing.

## 4. UI & Customization

### ManageLanguagesActivity

A user interface for discovering and installing additional language runtimes and tools.

- **Package List**: Dynamically fetches available packages.
- **Installation**: Leverages `AptBackgroundService` for background downloads.

### WorkspaceInitializer

Ensures the local environment is correctly set up on the first launch, creating necessary
directories and symlinks.
