package com.carnifex.rsyncmover.audit.entry;

import static com.carnifex.rsyncmover.audit.Type.SEEN;


public class SeenEntry extends Entry {

    private final String path;
    private final String server;

    public SeenEntry(final String path, final String server) {
        super(SEEN);
        this.path = path;
        this.server = server;
    }

    @Override
    public String format() {
        return server + ": " + path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SeenEntry seenEntry = (SeenEntry) o;

        if (!path.equals(seenEntry.path)) return false;
        return server.equals(seenEntry.server);

    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + server.hashCode();
        return result;
    }
}
