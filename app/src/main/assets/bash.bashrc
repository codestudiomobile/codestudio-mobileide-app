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

# 2.5 CCR (Compile & Run) Helper
# This ensures g++ and gcc point to our wrapper which handles automatic execution,
# cleanup, and executes in a safe environment.
alias csmide-compile='ccr'
alias ccr-gcc='ccr -mode gcc'
alias ccr-gpp='ccr -mode g++'
alias cscr-mono='cscr'

# Clear the screen
printf "\033[H\033[2J"

# 3. Code Studio Mobile ASCII Art
printf "\033[34m"
if [ -f "$PREFIX/etc/termux/banner.txt" ]; then
    cat "$PREFIX/etc/termux/banner.txt"
fi
printf "\033[0m\n"

# 4. Prompt Logic: Dynamic Title Resolution
# This function is executed before every prompt to ensure the title is up-to-date.
_update_prompt_title() {
    PROMPT_TITLE=$(cat "$PREFIX/etc/termux/title.txt" 2>/dev/null || echo "Code Studio Mobile IDE")
}

# Add the update function to PROMPT_COMMAND
if [[ ! "$PROMPT_COMMAND" =~ _update_prompt_title ]]; then
    PROMPT_COMMAND="_update_prompt_title${PROMPT_COMMAND:+;$PROMPT_COMMAND}"
fi

# Define the final custom prompt using the resolved variable
PS1='\[\033[34m\]┌──(\[\033[0m\]$PROMPT_TITLE\[\033[34m\])-[\[\033[32m\]\w\[\033[34m\]]\n\[\033[34m\]└─\[\033[0m\]\$ '

# Show storage warning if access was denied
if [ "$STORAGE_ACCESS_DENIED" -eq 1 ]; then
    printf "\033[33m[!] Warning: Storage access denied. Please grant permission by running the command termux-setup-storage.\033[0m\n"
fi

# Change directory to the opened folder (handles both default ~ and specified paths)
cd "$OPENED_FOLDER" 2>/dev/null || cd "$HOME"
