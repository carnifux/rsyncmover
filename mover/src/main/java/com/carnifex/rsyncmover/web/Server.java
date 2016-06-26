package com.carnifex.rsyncmover.web;


import com.carnifex.rsyncmover.audit.Audit;
import fi.iki.elonen.NanoHTTPD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class Server extends NanoHTTPD {

    private static final Logger logger = LogManager.getLogger();

    private final Audit audit;

    public Server(final int port, final Audit audit) {
        super(port);
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
        } else {
            return newFixedLengthResponse(audit.formatAll());
        }
    }

    public void shutdown() {
        this.stop();
    }
}
