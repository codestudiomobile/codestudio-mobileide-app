package com.cs.ide.app;
public interface ExecutionListener {
    void onOutputLine(String line, boolean isError);
    void onExecutionComplete(int exitCode);
}
