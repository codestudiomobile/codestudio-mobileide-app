package com.cs.ide.app.execution;

/**
 * ExecutionListener is an interface for receiving updates during the execution of a process.
 */
public interface ExecutionListener {
    /**
     * Called when a new line of output is received from the process.
     *
     * @param line    The output line.
     * @param isError True if the line is from the error stream.
     */
    void onOutputLine(String line, boolean isError);

    /**
     * Called when the execution of the process is complete.
     *
     * @param exitCode The exit code of the process.
     */
    void onExecutionComplete(int exitCode);
}
