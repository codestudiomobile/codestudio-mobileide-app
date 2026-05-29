# ==============================================================================
# CodeStudio Bash Configuration (bash.bashrc)
# ==============================================================================
# Description: Core shell environment initialization. Handles pathing,
#              IDE synchronization, storage setup, and prompt styling.
# ==============================================================================

# 1. Environment: Set PATH immediately for command availability
export PATH=$PREFIX/bin:$PATH
export LANG=en_US.UTF-8

# Implementation of OPENED_FOLDER logic
# Default to $HOME if not provided by the app
if [ -z "$OPENED_FOLDER" ]; then
    export OPENED_FOLDER="$HOME"
else
    # Convert raw Android storage paths to Termux storage symlinks for a better IDE experience
    if [[ "$OPENED_FOLDER" == "/storage/emulated/0"* ]]; then
        export OPENED_FOLDER="$HOME/storage/shared${OPENED_FOLDER#/storage/emulated/0}"
    elif [[ "$OPENED_FOLDER" == "/sdcard"* ]]; then
        export OPENED_FOLDER="$HOME/storage/shared${OPENED_FOLDER#/sdcard}"
    fi
fi

# Run termux-setup-storage if not already initialized
if [ ! -d "$HOME/storage" ] && command -v termux-setup-storage > /dev/null; then
    termux-setup-storage > /dev/null 2>&1
fi

# Check for storage access and prepare warning if denied
STORAGE_ACCESS_DENIED=0
if [ -d "$HOME/storage" ]; then
    # Check if the 'shared' symlink target is accessible
    if ! ls -Ld "$HOME/storage/shared" > /dev/null 2>&1; then
        STORAGE_ACCESS_DENIED=1
    fi
fi

# Command history tweaks
shopt -s histappend
shopt -s histverify
export HISTCONTROL=ignoreboth

# Default command line prompt settings
PROMPT_DIRTRIM=0

# 2. Fixed command-not-found handle (corrected quotes and line endings)
if [ -x "$PREFIX/libexec/termux/command-not-found" ]; then
    command_not_found_handle() {
       "$PREFIX/libexec/termux/command-not-found" "$1"
    }
fi

# Load bash completion if available
[ -r "$PREFIX/share/bash-completion/bash_completion" ] && . "$PREFIX/share/bash-completion/bash_completion"

# Clear the screen
printf "\033[H\033[2J"

# 3. Code Studio Mobile ASCII Art
printf "\033[34m"
if [ -f "$PREFIX/etc/termux/banner.txt" ]; then
    cat "$PREFIX/etc/termux/banner.txt"
fi
printf "\033[0m\n"

if [ -f "$PREFIX/etc/termux/title.txt" ]; then
    PROMPT_TITLE=$(cat "$PREFIX/etc/termux/title.txt")
else
    PROMPT_TITLE="Code Studio Mobile IDE"
fi

# 4. Final custom prompt (Correctly escaped for Bash)
PS1='\[\033[34m\]┌──(\[\033[0m\]$PROMPT_TITLE\[\033[34m\])-[\[\033[32m\]\w\[\033[34m\]]\n\[\033[34m\]└─\[\033[0m\]\$ '

# Show storage warning if access was denied
if [ "$STORAGE_ACCESS_DENIED" -eq 1 ]; then
    printf "\033[33m[!] Warning: Storage access denied. Please grant permission by running the command termux-setup-storage.\033[0m\n"
fi

# Change directory to the opened folder (handles both default ~ and specified paths)
cd "$OPENED_FOLDER" 2>/dev/null || cd "$HOME"
