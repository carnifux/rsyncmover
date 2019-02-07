package com.carnifex.rsyncmover.audit.entry;


import com.carnifex.rsyncmover.audit.Type;


public class NotificationEntry extends Entry {

    private static final long serialVersionUID = 5620290126355490889L;
    private final String path;

    public NotificationEntry(final Type type, final String message) {
        super(type);
        this.path = message;
    }

    @Override
    public String format() {
        return path;
    }
}
