#!/bin/bash
# ==============================================================================
# CodeStudio ASCII Banner Customizer
# ==============================================================================
# Description: Generates a stylized ASCII art banner from input text and
#              updates the terminal's greeting banner.
# ==============================================================================

# 1. Validation: Ensure input text is provided
if [ -z "$1" ]; then
    echo "Usage: $0 \"your text here\""
    exit 1
fi

# Configuration: Path to the persistent banner metadata
BANNER_FILE="$PREFIX/etc/termux/banner.txt"

# 2. Preparation: Ensure environment structure is ready
mkdir -p "$(dirname "$BANNER_FILE")"

# Normalize input to uppercase for character mapping
input=$(echo "$1" | tr '[:lower:]' '[:upper:]')

# Initialization: Character row mappings (ASCII Font Definition)
declare -A r1 r2 r3 r4 r5 r6

# --- ASCII CHARACTER MAP (A-Z) ---
# Each character is defined across 6 rows to form a block font.
r1[A]="░█████═╗░" ; r2[A]="██║░░██║░" ; r3[A]="███████║░" ; r4[A]="██╔═╗██║░" ; r5[A]="██║░║██║░" ; r6[A]="╚═╝░╚══╝░"
r1[B]="██████╗░░" ; r2[B]="██░░░██║░" ; r3[B]="██████║░░" ; r4[B]="██░░░██║░" ; r5[B]="██████╝░░" ; r6[B]="╚═════╝░░"
r1[C]="░██████╗░" ; r2[C]="██╔════╝░" ; r3[C]="██║░░░░░░" ; r4[C]="██╚════╗░" ; r5[C]="╚██████║░" ; r6[C]="░╚═════╝░"
r1[D]="███████═╗░░" ; r2[D]="██╔═══╗██║░" ; r3[D]="██║░░░║██║░" ; r4[D]="██╚═══╝██║░" ; r5[D]="███████═╝░░" ; r6[D]="╚═════╝░░░░"
r1[E]="░██████╗░" ; r2[E]="██╚════╗░" ; r3[E]="███████║░" ; r4[E]="██╚════╗░" ; r5[E]="╚██████║░" ; r6[E]="░╚═════╝░"
r1[F]="░██████╗░" ; r2[F]="██╚════╗░" ; r3[F]="███████║░" ; r4[F]="██╔════╝░" ; r5[F]="██║░░░░░░" ; r6[F]="╚═╝░░░░░░"
r1[G]="░██████╗░" ; r2[G]="██╚════╗░" ; r3[G]="██ ████║░" ; r4[G]="██   ██║░" ; r5[G]="╚████╔═╝░" ; r6[G]="░╚═══╝░░░"
r1[H]="██╗░░░██╗░" ; r2[H]="██║░░░██║░" ; r3[H]="████████║░" ; r4[H]="██╔══╗██║░" ; r5[H]="██║░░║██║░" ; r6[H]="╚═╝░░╚══╝░"
r1[I]="████████╗░" ; r2[I]="╚══██╔══╝░" ; r3[I]="░░░██║░░░░" ; r4[I]="░░░██║░░░░" ; r5[I]="████████╝░" ; r6[I]="╚══════╝░░"
r1[J]="░░╔█████╗░" ; r2[J]="░░╚═══██║░" ; r3[J]="░░░░░░██║░" ; r4[J]="░╔██░░██║░" ; r5[J]="░╚╗████╝░░" ; r6[J]="░░╚════╝░░"
r1[K]="██╗░░██╗░" ; r2[K]="██║░██╝░░" ; r3[K]="████╝░░░░" ; r4[K]="██║░██═╗░" ; r5[K]="██║░░██║░" ; r6[K]="╚═╝░░╚═╝░"
r1[L]="██╗░░░░░░" ; r2[L]="██║░░░░░░" ; r3[L]="██║░░░░░░" ; r4[L]="██╚════╗░" ; r5[L]="╚██████║░" ; r6[L]="░╚═════╝░"
r1[M]="████╗░████╗░" ; r2[M]="██░████░██║░" ; r3[M]="██╔╗██╔═██║░" ; r4[M]="██║╚══╝░██║░" ; r5[M]="██║░░░░░██║░" ; r6[M]="╚═╝░░░░░╚═╝░"
r1[N]="████╗░░░██╗░" ; r2[N]="██░██╗░░██║░" ; r3[N]="██╔╗██╗░██║░" ; r4[N]="██║╚═██░██║░" ; r5[N]="██║░░░████║░" ; r6[N]="╚═╝░░░░░╚═╝░"
r1[O]="░░██████╗░░░" ; r2[O]="██╔════╗██╗░" ; r3[O]="██║░░░░║██║░" ; r4[O]="██╚════╝██║░" ; r5[O]="░╚███████╝░░" ; r6[O]="░░╚═════╝░░░"
r1[P]="██████═╗░" ; r2[P]="██╔═╗██║░" ; r3[P]="█████╔═╝░" ; r4[P]="██╔══╝░░░" ; r5[P]="██║░░░░░░" ; r6[P]="╚═╝░░░░░░"
r1[Q]="░░███████═╗░░" ; r2[Q]="░█░░░░░░░█╚╗░" ; r3[Q]="██░░██░░░██║░" ; r4[Q]="░█░░░██░░█═╝░" ; r5[Q]="░░███░██░░░░░" ; r6[Q]="░░░░░░░░██░░░"
r1[R]="██████═╗░░" ; r2[R]="██╔═╗██╝░░" ; r3[R]="█████░░░░░" ; r4[R]="██╔═╝██═╗░" ; r5[R]="██║░░░██║░" ; r6[R]="╚═╝░░░╚═╝░"
r1[S]="░░██████╗░" ; r2[S]="░██╔════╝░" ; r3[S]="░╚█████╗░░" ; r4[S]="░░░╚═══██╗" ; r5[S]="░╚██████╔╝" ; r6[S]="░░╚═════╝░"
r1[T]="████████╗░" ; r2[T]="╚══██╔══╝░" ; r3[T]="░░░██║░░░░" ; r4[T]="░░░██║░░░░" ; r5[T]="░░░██║░░░░" ; r6[T]="░░░╚═╝░░░░"
r1[U]="██╗░░██╗░" ; r2[U]="██║░░██║░" ; r3[U]="██║░░██║░" ; r4[U]="██║░░██║░" ; r5[U]="███████║░" ; r6[U]="╚══════╝░"
r1[V]="██╗░░░░░░██╗░" ; r2[V]="╚██╗░░░░██╔╝░" ; r3[V]="░╚██╗░░██╔╝░░" ; r4[V]="░░╚██╗██╔╝░░░" ; r5[V]="░░░╚███╔╝░░░░" ; r6[V]="░░░░╚══╝░░░░░"
r1[W]="██░░░░░░██╗░" ; r2[W]="██░╔══╗░██║░" ; r3[W]="██╔╝██╚╗██║░" ; r4[W]="██║████║██║░" ; r5[W]="████╝░████║░" ; r6[W]="╚══╝░░╚══╝░░"
r1[X]="██╗░░░ ██╗░" ; r2[X]="░ ██╗ ██╔╝░" ; r3[X]="░░╔╝██╚═╗░░" ; r4[X]="╔╝██╝ ██╚╗░" ; r5[X]="██╔╝░ ░██║░" ; r6[X]="╚═╝░░░ ╚═╝░"
r1[Y]="░██╗░░░██╗░" ; r2[Y]="░╚██╗░██╔╝░" ; r3[Y]="░░╚████╔╝░░" ; r4[Y]="░░░░██╔╝░░░" ; r5[Y]="░░░░██║░░░░" ; r6[Y]="░░░░╚═╝░░░░"
r1[Z]="████████╗░" ; r2[Z]="░░░░░██╔╝░" ; r3[Z]="░░░██╔═╝░░" ; r4[Z]="░██══╝░░░░" ; r5[Z]="████████╗░" ; r6[Z]="╚═══════╝░"

# Helper: Logic to compile and print a single word block horizontally
print_word_block() {
    local word="$1"
    local line1="" line2="" line3="" line4="" line5="" line6=""

    for (( i=0; i<${#word}; i++ )); do
        local char="${word:$i:1}"

        # Build lines if the key exists inside our map
        if [ -n "${r1[$char]}" ]; then
            line1+="${r1[$char]}"
            line2+="${r2[$char]}"
            line3+="${r3[$char]}"
            line4+="${r4[$char]}"
            line5+="${r5[$char]}"
            line6+="${r6[$char]}"
        fi
    done

    # Print out the stacked character strings
    echo "$line1"
    echo "$line2"
    echo "$line3"
    echo "$line4"
    echo "$line5"
    echo "$line6"
}

# --- CORE EXECUTION ---

# 3. Generation: Construct the ASCII banner and write to file
{
    # Process word-by-word to maintain structural integrity across lines
    for word in $input; do
        print_word_block "$word"
        echo "" # Logical spacing between word blocks
    done
} > "$BANNER_FILE"

# 4. Synchronization: Reload bash configuration to apply changes
if [ -f "$PREFIX/etc/bash.bashrc" ]; then
    source "$PREFIX/etc/bash.bashrc"
fi
