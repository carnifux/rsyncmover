package com.carnifex.rsyncmover.web;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.mover.io.Mover;
import com.carnifex.rsyncmover.sync.Syncer;
import fi.iki.elonen.NanoHTTPD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.io.Streams;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Server extends NanoHTTPD {

    private static final Logger logger = LogManager.getLogger();

    private final Syncer syncer;
    private final Audit audit;
    private final List<Mover> movers;

    public Server(final int port, final Syncer syncer, final List<Mover> movers, final Audit audit) {
        super(port);
        this.syncer = syncer;
        this.movers = movers;
        this.audit = audit;
        logger.info("Web server successfully initialized on port " + port);
        try {
            this.start(SOCKET_READ_TIMEOUT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response serve(final IHTTPSession session) {
        final String uri = session.getUri();
        if (uri.endsWith("downloadstatus")) {
            return newFixedLengthResponse(audit.getDownloadWatcherStatuses());
        } else if (uri.endsWith("findmoverfor")) {
            try {
                final HashMap<String, String> map = new HashMap<>();
                session.parseBody(map);
                final String content = map.get("postData");
                final Optional<Mover> chosen = movers.stream()
                        .filter(mover -> mover.shouldSubmit(Paths.get(content)))
                        .sorted((a, b) -> -Integer.compare(a.getPriority(), b.getPriority()))
                        .findFirst();
                if (chosen.isPresent()) {
                    return newFixedLengthResponse(chosen.get().getName());
                }
                return newFixedLengthResponse("No movers matched");
            } catch (Exception e) {
                logger.error("", e);
                return newFixedLengthResponse("Exception: " + e.getMessage());
            }
        } else if (uri.endsWith("checkservers")) {
            if (syncer != null) {
                syncer.interrupt();
            }
            return newFixedLengthResponse("");
        } else {
            return newFixedLengthResponse(audit.formatAll());
        }
    }

    public void shutdown() {
        this.stop();
    }
}
