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

### How to Apply a Custom Banner

You can use the built-in `apply-banner` command inside the terminal:

1. Open a new terminal.
2. Run the following command:
   ```bash
   apply-banner "YOUR TEXT HERE"
   ```
3. Restart the terminal to see the changes.

### Manual Customization

The banner text is stored in:
`$PREFIX/etc/termux/banner.txt`

You can manually edit this file with any text or ASCII art you prefer:

```bash
nano $PREFIX/etc/termux/banner.txt
```

---

## 2. Custom Prompt Title

The prompt title is the name displayed in the terminal prompt (e.g., `(Code Studio Mobile IDE)`).

### Default Appearance

The default prompt is a two-line Material-style prompt that looks like this:

```text
┌──(Code Studio Mobile IDE)-[~]
└─$ 
```

- **Blue Elements**: The borders (`┌──`, `)-[`, `]`, `└─`) are blue.
- **Green Elements**: The current working directory (`~`) is green.
- **White/Standard**: The prompt title and the final `$` are the default text color.

### How to Change the Prompt Title

You can use the built-in `apply-title` command inside the terminal:

1. Open a new terminal.
2. Run the following command:
   ```bash
   apply-title "YOUR NEW TITLE"
   ```
3. Restart the terminal to see the changes.

### Manual Customization

The prompt configuration is defined in the `.bashrc` file.

1. Open the `.bashrc` file in the editor or via terminal:
   ```bash
   nano $HOME/.bashrc
   ```
2. Look for the `PROMPT_TITLE` variable:
   ```bash
   PROMPT_TITLE="Code Studio Mobile IDE"
   ```
3. Change the value to your desired title:
   ```bash
   PROMPT_TITLE="My Project"
   ```
4. Save the file and restart your terminal session.

---

## 3. Editor Themes

Editor themes can be adjusted in **Settings > Editor Settings**. You can choose from built-in themes
or apply custom color schemes.

---

## 4. Development Note

Customization features are being expanded. In future updates, we plan to add a dedicated UI for
banner and prompt management.
