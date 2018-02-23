package com.carnifex.rsyncmover.audit.entry;


import com.carnifex.rsyncmover.audit.Type;


public class NotificationEntry extends Entry {

    private final String path;

    public NotificationEntry(final String message) {
        super(Type.MOVED);
        this.path = message;
    }

    @Override
    public String format() {
        return path;
    }
}
