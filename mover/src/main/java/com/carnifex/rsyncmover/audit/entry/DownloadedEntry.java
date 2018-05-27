package com.carnifex.rsyncmover.audit.entry;

import static com.carnifex.rsyncmover.audit.Type.DOWNLOADED;


public class DownloadedEntry extends Entry {

    private final String path;
    private final String server;

    public DownloadedEntry(final String path, final String server) {
        super(DOWNLOADED);
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

        final DownloadedEntry that = (DownloadedEntry) o;

        if (!path.equals(that.path)) return false;
        return server.equals(that.server);

    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + server.hashCode();
        return result;
    }
}
