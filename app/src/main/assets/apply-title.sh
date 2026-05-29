#!/bin/bash
# ==============================================================================
# CodeStudio Prompt Title Customizer
# ==============================================================================
# Description: Updates the terminal's prompt title both persistently and
#              immediately. It modifies the core bash configuration and
#              synchronizes with a dedicated title metadata file.
# ==============================================================================

# 1. Validation: Ensure a title is provided
if [ -z "$1" ]; then
    echo "Error: No title provided."
    echo "Usage: apply-title \"Your New Title\""
    exit 1
fi

NEW_TITLE="$1"

# Automatically infer environment prefix if not explicitly set
if [ -z "$PREFIX" ]; then
    PREFIX="/data/data/com.cs.ide/files/usr"
fi

BASHRC_FILE="$PREFIX/etc/bash.bashrc"
TITLE_FILE="$PREFIX/etc/termux/title.txt"

# 2. Persistence: Ensure the metadata directory exists and save the title
mkdir -p "$(dirname "$TITLE_FILE")"

# Save to dedicated metadata file for persistence across session initializations
echo "$NEW_TITLE" > "$TITLE_FILE"

# 3. Synchronization: Update the active bash.bashrc for immediate reflection
if [ -f "$BASHRC_FILE" ]; then
    # Patch the configuration variable if it exists
    if grep -q 'PROMPT_TITLE=' "$BASHRC_FILE"; then
        sed -i "s|PROMPT_TITLE=.*|PROMPT_TITLE=\"$NEW_TITLE\"|" "$BASHRC_FILE"
    fi
    # Force immediate reload of the configuration in the current context
    source "$BASHRC_FILE"
fi
