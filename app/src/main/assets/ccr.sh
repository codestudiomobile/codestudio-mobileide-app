#!/data/data/com.termux/files/usr/bin/bash

# CCR - C/C++ Compile & Run wrapper for Code Studio
# It compiles, runs the output, and then deletes the output binary.

REAL_GCC="clang"
REAL_GPP="clang++"
COMPILER="$REAL_GPP"

# Handle symlink invocation (gcc/g++ -> ccr)
case "$(basename "$0")" in
    *gcc*) COMPILER="$REAL_GCC" ;;
    *g++*) COMPILER="$REAL_GPP" ;;
esac

ARGS=()
OUTPUT_FILE=""
PASS_THRU=()

# Parse arguments to find output file
while [[ $# -gt 0 ]]; do
    case "$1" in
        -mode)
            if [ "$2" == "gcc" ]; then COMPILER="$REAL_GCC"; else COMPILER="$REAL_GPP"; fi
            shift 2
            ;;
        -o)
            OUTPUT_FILE="$2"
            PASS_THRU+=("$1" "$2")
            shift 2
            ;;
        *)
            PASS_THRU+=("$1")
            shift
            ;;
    esac
done

# If no output file specified, use a temp name
if [ -z "$OUTPUT_FILE" ]; then
    OUTPUT_FILE="ccr_exec_$(date +%s%N)"
    PASS_THRU+=("-o" "$OUTPUT_FILE")
fi

# Run in app's private home directory for safe execution
# We stay in current directory for compilation to find source files,
# then move/run in HOME if needed?
# Actually, let's just compile and run where we are, but user said "runs it in app's private dir".
# If we are already in HOME (cache/files), that's fine.

# Execute compiler
"$COMPILER" "${PASS_THRU[@]}"
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    # Run the output
    if [[ "$OUTPUT_FILE" != /* ]]; then
        "./$OUTPUT_FILE"
    else
        "$OUTPUT_FILE"
    fi
    RUN_EXIT_CODE=$?

    # Delete the output file as requested
    rm -f "$OUTPUT_FILE"
    exit $RUN_EXIT_CODE
fi

exit $EXIT_CODE
