package com.carnifex.rsyncmover.audit.entry;

import static com.carnifex.rsyncmover.audit.Type.DUPLICATE;


public class DuplicateEntry extends Entry {

    private final String path;

    public DuplicateEntry(final String path) {
        super(DUPLICATE);
        this.path = path;
    }

    @Override
    public String format() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DuplicateEntry that = (DuplicateEntry) o;

        return path.equals(that.path);

    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
