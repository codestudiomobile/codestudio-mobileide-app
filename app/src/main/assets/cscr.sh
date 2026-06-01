#!/data/data/com.termux/files/usr/bin/bash

# CSCR - C# Compile & Run wrapper for Code Studio
# It compiles using mcs, runs the output with mono, and then deletes the output .exe.

REAL_MCS="mcs"
REAL_MONO="mono"

if [ $# -eq 0 ]; then
    echo "Usage: cscr <source_file.cs> [args...]"
    exit 1
fi

SOURCE_FILE=""
ARGS=()
PASS_THRU=()

# Basic argument parsing
for arg in "$@"; do
    if [[ "$arg" == *.cs ]]; then
        SOURCE_FILE="$arg"
    else
        ARGS+=("$arg")
    fi
done

if [ -z "$SOURCE_FILE" ]; then
    echo "Error: No .cs source file specified."
    exit 1
fi

# Determine output file name
OUTPUT_EXE="${SOURCE_FILE%.cs}.exe"

# 1. Compile
$REAL_MCS "$SOURCE_FILE"
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    # 2. Run
    $REAL_MONO "$OUTPUT_EXE" "${ARGS[@]}"
    RUN_EXIT_CODE=$?

    # 3. Cleanup output binary
    rm -f "$OUTPUT_EXE"
    exit $RUN_EXIT_CODE
fi

exit $EXIT_CODE
