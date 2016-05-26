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
        if (throwable == null) {
            return message;
        }
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        // printwriter doesn't give us new lines
        return message + "\n" + stringWriter.toString().replace("\n", "<br />");
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
