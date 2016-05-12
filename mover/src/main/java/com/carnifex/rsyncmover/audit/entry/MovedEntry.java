package com.carnifex.rsyncmover.audit.entry;

import java.io.File;

import static com.carnifex.rsyncmover.audit.Type.MOVED;


public class MovedEntry extends Entry {

    private static final String PATH_SEPARATOR = File.separator.equals("\\") ? "\\\\" : File.separator;
    private final String from;
    private final String to;
    private final String operation;

    public MovedEntry(final String from, final String to, final String operation) {
        super(MOVED);
        this.from = from;
        this.to = to;
        this.operation = operation;
    }

    @Override
    public String format() {
        final String[] fromSplit = from.split(PATH_SEPARATOR);
        return operation + ": " + fromSplit[fromSplit.length - 1] + " -> " + to.substring(0, to.lastIndexOf(File.separator));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MovedEntry that = (MovedEntry) o;

        if (!from.equals(that.from)) return false;
        if (!to.equals(that.to)) return false;
        return operation.equals(that.operation);

    }

    @Override
    public int hashCode() {
        int result = from.hashCode();
        result = 31 * result + to.hashCode();
        result = 31 * result + operation.hashCode();
        return result;
    }
}
