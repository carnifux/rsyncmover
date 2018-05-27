package com.carnifex.rsyncmover.audit.entry;


import com.carnifex.rsyncmover.audit.Type;


public class NotificationEntry extends Entry {

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
