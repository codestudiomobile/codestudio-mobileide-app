# 0. Ensure we are running under proot (faking root) if it's available and we aren't already.
# This ensures that every command executed goes through the proot for path compatibility.
if [ -z "$TERMUX_PROOT_ACTIVE" ] && [ -n "$TERMUX_APP__FILES_DIR" ]; then
    if [ -x "$TERMUX_APP__FILES_DIR/usr/bin/proot" ]; then
        export TERMUX_PROOT_ACTIVE=1

        # Define and create a virtual root to allow /data/data/com.termux paths to work
        # and to provide a writable area for dpkg etc.
        VROOT="$TERMUX_APP__FILES_DIR/proot_vroot"
        mkdir -p "$VROOT/data/data/com.termux/files"
        mkdir -p "$VROOT/data/data/com.cs.ide/files"

        # Re-exec with proot wrapping using a virtual root
        exec "$TERMUX_APP__FILES_DIR/usr/bin/proot" \
            -r "$VROOT" \
            -0 \
            -b /system \
            -b /dev \
            -b /proc \
            -b /sys \
            -b /apex \
            -b /linkerconfig \
            -b /sdcard \
            -b /storage \
            -b /data/app \
            -b /data/user \
            -b /data/dalvik-cache \
            -b "$TERMUX_APP__APK_PATH" \
            -b "$TERMUX_APP__FILES_DIR:/data/data/com.termux/files" \
            -b "$TERMUX_APP__FILES_DIR:/data/data/com.cs.ide/files" \
            -b "$TERMUX_APP__FILES_DIR/usr:/usr" \
            -b "$TERMUX_APP__FILES_DIR/home:/home" \
            -b "$TERMUX_APP__FILES_DIR/usr/tmp:/tmp" \
            /data/data/com.termux/files/usr/bin/bash "$@"
    fi
fi

# 1. Set PATH immediately so all subsequent commands are found
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

PROMPT_TITLE="Code Studio Mobile IDE"

# 4. Final custom prompt (Correctly escaped for Bash)
PS1='\[\033[34m\]┌──(\[\033[0m\]$PROMPT_TITLE\[\033[34m\])-[\[\033[32m\]\w\[\033[34m\]]\n\[\033[34m\]└─\[\033[0m\]\$ '

# Show storage warning if access was denied
if [ "$STORAGE_ACCESS_DENIED" -eq 1 ]; then
    printf "\033[33m[!] Warning: Storage access denied. Please grant permission by running the command termux-setup-storage.\033[0m\n"
fi

# Change directory to the opened folder (handles both default ~ and specified paths)
cd "$OPENED_FOLDER" 2>/dev/null || cd "$HOME"
