package com.carnifex.rsyncmover.web;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.mover.io.Mover;
import com.carnifex.rsyncmover.sync.Syncer;
import fi.iki.elonen.NanoHTTPD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;
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
        try {
            this.start(SOCKET_READ_TIMEOUT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Web server successfully initialized on port " + port);
    }

    @Override
    public Response serve(final IHTTPSession session) {
        final String uri = session.getUri();
        if (uri.endsWith("findmoverfor")) {
            try {
                final HashMap<String, String> map = new HashMap<>();
                session.parseBody(map);
                final String content = map.get("postData");
                final Optional<Mover> chosen = movers.stream()
                        .filter(mover -> mover.shouldSubmit(Paths.get(content)))
                        .sorted((a, b) -> -Integer.compare(a.getPriority(), b.getPriority()))
                        .findFirst();
                if (chosen.isPresent()) {
                    return newFixedLengthResponse(chosen.get().getName() + ": " + chosen.get().getTarget(Paths.get(content)).toAbsolutePath().toString());
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
        } else if (uri.endsWith("movestatus")) {
            return newFixedLengthResponse(audit.getMoveStatus());
        } else if (uri.endsWith("downloadstatus")) {
            return newFixedLengthResponse(audit.getDownloadWatcherStatuses());
        } else if (uri.endsWith("downloadqueuestatus")) {
            return newFixedLengthResponse(audit.getDownloadQueueStatus());
        } else {
            final boolean all = Boolean.valueOf(session.getParms().getOrDefault("all", "false"));
            return newFixedLengthResponse(audit.formatAll(all));
        }
    }

    public void shutdown() {
        this.stop();
    }
}
