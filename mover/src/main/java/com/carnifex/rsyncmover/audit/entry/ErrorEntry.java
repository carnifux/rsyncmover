package com.carnifex.rsyncmover.audit.entry;


import java.io.PrintWriter;
import java.io.StringWriter;

import static com.carnifex.rsyncmover.audit.Type.ERROR;

public class ErrorEntry extends Entry {

    private static final long serialVersionUID = 2243314384957467217L;
    private final String stacktrace;
    private final String message;

    public ErrorEntry(final String message) {
        this(message, null);
    }

    public ErrorEntry(final String message, final Throwable throwable) {
        super(ERROR);
        this.message = message;
        // don't store the throwable as we don't know if they're serializable
        this.stacktrace = throwable == null ? null : formatStackTrace(throwable);
    }

    private String formatStackTrace(final Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        // printwriter doesn't give us new lines
        return stringWriter.toString().replace("\n", "<br />");
    }

    @Override
    public String format() {
        if (stacktrace == null) {
            return message;
        }
        return message + "\n" + stacktrace;
    }

    @Override
    public boolean equals(final Object o) {
        // override the set behaviour, we want to keep each and every error
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
