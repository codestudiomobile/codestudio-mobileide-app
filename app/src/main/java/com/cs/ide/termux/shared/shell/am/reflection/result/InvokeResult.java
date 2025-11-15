package com.cs.ide.termux.shared.shell.am.reflection.result;

/**
 * Result for an invocation.
 */
public class InvokeResult {

    /** Whether invocation was successful. */
    public boolean success;
    /** The result {@link Object} for the invocation. */
    public Object value;

    public InvokeResult(boolean success, Object value) {
        this.success = success;
        this.value = value;
    }

}
