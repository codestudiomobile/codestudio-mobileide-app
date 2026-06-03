# Customization Guide

CodeStudio allows you to customize your development environment to suit your preferences.

## 1. Custom Terminal Banner

The terminal banner is the ASCII art shown when you open a new terminal session.

### Default Appearance

By default, the banner displays "CODE STUDIO MOBILE IDE" in a stylized block ASCII format. This is
how the default banner looks when you open a new terminal:

```text
░██████╗░░░██████╗░░░███████═╗░░░██████╗░
██╔════╝░██╔════╗██╗░██╔═══╗██║░██╚════╗░
██║░░░░░░██║░░░░║██║░██║░░░║██║░███████║░
██╚════╗░██╚════╝██║░██╚═══╝██║░██╚════╗░
╚██████║░░╚███████╝░░███████═╝░░╚██████║░
░╚═════╝░░░╚═════╝░░░╚═════╝░░░░░╚═════╝░

░░██████╗░████████╗░██╗░░██╗░███████═╗░░████████╗░░░██████╗░░░
░██╔════╝░╚══██╔══╝░██║░░██║░██╔═══╗██║░╚══██╔══╝░██╔════╗██╗░
░╚█████╗░░░░░██║░░░░██║░░██║░██║░░░║██║░░░░██║░░░░██║░░░░║██║░
░░░╚═══██╗░░░██║░░░░██║░░██║░██╚═══╝██║░░░░██║░░░░██╚════╝██║░
░╚██████╔╝░░░██║░░░░███████║░███████═╝░░████████╝░░╚███████╝░░
░░╚═════╝░░░░╚═╝░░░░╚══════╝░╚═════╝░░░░╚══════╝░░░░╚═════╝░░░

████╗░████╗░░░██████╗░░░██████╗░░████████╗░██╗░░░░░░░██████╗░
██░████░██║░██╔════╗██╗░██░░░██║░╚══██╔══╝░██║░░░░░░██╚════╗░
██╔╗██╔═██║░██║░░░░║██║░██████║░░░░░██║░░░░██║░░░░░░███████║░
██║╚══╝░██║░██╚════╝██║░██░░░██║░░░░██║░░░░██╚════╗░██╚════╗░
██║░░░░░██║░░╚███████╝░░██████╝░░████████╝░╚██████║░╚██████║░
╚═╝░░░░░╚═╝░░░╚═════╝░░░╚═════╝░░╚══════╝░░░╚═════╝░░╚═════╝░

████████╗░███████═╗░░░██████╗░
╚══██╔══╝░██╔═══╗██║░██╚════╗░
░░░██║░░░░██║░░░║██║░███████║░
░░░██║░░░░██╚═══╝██║░██╚════╗░
████████╝░███████═╝░░╚██████║░
╚══════╝░░╚═════╝░░░░░╚═════╝░
```

### How to Customize the Banner and Prompt Visually (Recommended)

CodeStudio provides a dedicated, rich graphical interface to update your terminal banner and prompt
title without writing any terminal commands:

1. Open the sidebar navigation menu and select **Settings**.
2. Tap **Customize Terminal** to open **`CustomizationActivity`**.
3. In the panel:
    * **Prompt Title Input**: Enter your desired prompt name. A live visual preview showing
      `Prompt Preview: [Your Name] $` will update instantly in real time.
    * **Banner Text Input**: Type the text you want to display as your greeting banner. The utility
      will stitch block letters from a 2D Unicode lookup map (`asciiMap`) and display a direct
      preview of the final block ASCII art banner.
4. Tap **Apply Customization**. CodeStudio will persistently save these strings in
   `SharedPreferences`, copy the customization scripts (`apply-banner.sh` and `apply-title.sh`) into
   internal private executables directory, and execute them inside the Termux pseudo-terminal
   context to apply changes instantly.
5. All currently active terminal sessions and any newly spawned terminal tabs will immediately
   reflect your personalized prompt title and ASCII greeting banner without requiring a manual shell
   restart!

---

### How to Apply a Custom Banner via CLI

You can also run the built-in customizer command directly inside the terminal:

1. Open an active terminal tab.
2. Run the following command:
   ```bash
   apply-banner "YOUR TEXT HERE"
   ```
3. The ASCII block letter generator will compile the block characters horizontally and write them to
   the persistent configuration folder.
4. Open a new terminal tab to see your new custom banner instantly.

### Manual Customization

The banner text layout is stored persistently in Termux's local configuration path:
`$PREFIX/etc/termux/banner.txt`

You can manually edit this file with any custom ASCII art or system variables using standard
editors:

```bash
nano $PREFIX/etc/termux/banner.txt
```

---

## 2. Custom Prompt Title

The prompt title is the name displayed inside the brackets of the interactive prompt (e.g.,
`(Code Studio Mobile IDE)`).

### Default Appearance

The terminal displays a customized two-line Material prompt layout:

```text
┌──(Code Studio Mobile IDE)-[~]
└─$ 
```

* **Blue Elements**: The frame structure borders (`┌──`, `)-[`, `]`, `└─`) are formatted in light
  blue.
* **Green Elements**: The current working directory (`~`) is rendered in light green.
* **Prompt Variable**: The prompt title is read dynamically from `$PROMPT_TITLE`.

### How to Change the Prompt Title via CLI

To update your terminal prompt title via the command line:

1. Open an active terminal tab.
2. Run the following command:
   ```bash
   apply-title "YOUR NEW TITLE"
   ```
3. The prompt customizer writes this name to `$PREFIX/etc/termux/title.txt`.
4. Because the core environment initialization script (`.bashrc`) hooks into the shell's
   `PROMPT_COMMAND` execution chain, the terminal reads this file before every new prompt line. *
   *Your prompt updates immediately across all open terminal tabs without requiring a shell restart!
   **

### Manual Customization

The dynamic title resolution is defined in the `.bashrc` initialization file:

1. Open the `.bashrc` configuration file in the IDE editor or via terminal:
   ```bash
   nano $HOME/.bashrc
   ```
2. Locate the prompt update handler and title loader:
   ```bash
   _update_prompt_title() {
       PROMPT_TITLE=$(cat "$PREFIX/etc/termux/title.txt" 2>/dev/null || echo "Code Studio Mobile IDE")
   }
   ```
3. You can modify these variables or append custom prompt parameters to `PS1` manually. Save your
   changes and reload your shell.

---

## 3. Environment Management

### Restart Terminal

If you've modified global environment variables, installed new packages that require a shell
refresh, or encounter a hung process, you can use the built-in restart command:

1. Open an active terminal tab.
2. Run the following command:
   ```bash
   restart-terminal
   ```
3. This will broadcast a restart signal to the IDE, kill all active background terminal sessions,
   and re-initialize the terminal subsystem, ensuring a clean slate.

---

## 4. Editor Themes

Editor themes can be adjusted in **Settings > Editor Settings**. You can choose from built-in themes
or apply custom color schemes.

---

## 5. Development Note

We are continuously improving the customization engine. Future updates will include more ASCII fonts
and deeper integration with shell themes.
