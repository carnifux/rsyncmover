package com.carnifex.rsyncmover.audit.entry;


import com.carnifex.rsyncmover.audit.Type;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public abstract class Entry implements Serializable {

    private static final ZoneOffset offset = ZonedDateTime.now().getOffset();
    private final Type type;
    private final long timestamp;

    protected Entry(final Type type) {
        this.type = type;
        this.timestamp = ZonedDateTime.now().toEpochSecond();
    }

    public final Type getType() {
        return type;
    }

    public final LocalDateTime getCreatedAt() {
        return LocalDateTime.ofEpochSecond(timestamp, 0, offset);
    }

    public abstract String format();
}
