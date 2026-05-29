#!/bin/bash

# 1. Check if a title argument was provided
if [ -z "$1" ]; then
    echo "Error: No title provided."
    echo "Usage: apply-title \"Your New Title\""
    exit 1
fi

NEW_TITLE="$1"
# We target bash.bashrc as it's the primary config used in CodeStudio
BASHRC_FILE="$PREFIX/etc/bash.bashrc"

# 2. Check if PROMPT_TITLE already exists in the file
if grep -q 'PROMPT_TITLE=' "$BASHRC_FILE"; then
    # If it exists, replace the existing line
    # We use | as a delimiter in sed because the title might contain /
    sed -i "s|PROMPT_TITLE=.*|PROMPT_TITLE=\"$NEW_TITLE\"|" "$BASHRC_FILE"
    echo "Updated PROMPT_TITLE to: \"$NEW_TITLE\""
else
    # If it doesn't exist, append it to the end of the file
    echo "" >> "$BASHRC_FILE"
    echo "PROMPT_TITLE=\"$NEW_TITLE\"" >> "$BASHRC_FILE"
    echo "Added PROMPT_TITLE=\"$NEW_TITLE\" to $BASHRC_FILE"
fi

echo "Done! Please restart your terminal to see the changes."

if [ -f "$PREFIX/etc/bash.bashrc" ]; then
    source "$PREFIX/etc/bash.bashrc"
fi
