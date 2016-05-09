package com.carnifex.rsyncmover.audit.entry;


import java.io.PrintWriter;
import java.io.StringWriter;

import static com.carnifex.rsyncmover.audit.Type.ERROR;

public class ErrorEntry extends Entry {

    private final Throwable throwable;
    private final String message;

    public ErrorEntry(final String message, final Throwable throwable) {
        super(ERROR);
        this.message = message;
        this.throwable = throwable;
    }

    @Override
    public String format() {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return message + "\n" + stringWriter.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ErrorEntry that = (ErrorEntry) o;

        if (!throwable.equals(that.throwable)) return false;
        return message.equals(that.message);

    }

    @Override
    public int hashCode() {
        int result = throwable.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }
}
