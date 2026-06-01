#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# CodeStudio Prompt Title Customizer
# ==============================================================================
# Description: Updates the terminal's prompt title. It writes the new title
#              to a metadata file which is dynamically read by the shell's
#              prompt (PS1) configuration.
# ==============================================================================

# 1. Validation: Ensure a title is provided
if [ -z "$1" ]; then
    echo "Error: No title provided."
    echo "Usage: apply-title \"Your New Title\" or apply-title Your New Title"
    exit 1
fi

NEW_TITLE="$*"

# Automatically infer environment prefix if not explicitly set
if [ -z "$PREFIX" ]; then
    PREFIX="/data/data/com.csmide/files/usr"
fi

TITLE_FILE="$PREFIX/etc/termux/title.txt"

# 2. Update Title: Ensure the metadata directory exists and save the title
mkdir -p "$(dirname "$TITLE_FILE")"

# Save to dedicated metadata file.
# Because the shell prompt is configured to read this file dynamically,
# the change will reflect immediately in all open terminals.
echo "$NEW_TITLE" > "$TITLE_FILE"

# No need to source bashrc or sed variables anymore; the prompt handles it.
