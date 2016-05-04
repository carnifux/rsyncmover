package com.carnifex.rsyncmover.web;


import com.carnifex.rsyncmover.audit.Audit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {

    private static final Logger logger = LogManager.getLogger(Server.class);

    private final ServerSocket socket;
    private final Audit audit;

    public Server(final int port, final Audit audit) {
        try {
            this.socket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.audit = audit;
        logger.info("Web server successfully initialized on port " + port);
        start();
    }

    @Override
    public void run() {
        for (;;) {
            try {
                final Socket s = socket.accept();
                new ServerThread(s).start();
            } catch (IOException e) {
                final String msg = "IOException from server socket";
                logger.error(msg, e);
                audit.addError(msg, e);
            }

        }
    }

    private class ServerThread extends Thread {

        private final Socket s;

        private ServerThread(final Socket s) {
            super("ServerThread");
            this.s = s;
        }

        @Override
        public void run() {
            logger.info("Request received from " + s.getInetAddress().toString() + ", sending audit summary");
            try {
                final OutputStream os = s.getOutputStream();
                os.write(audit.formatAll().getBytes());
                os.flush();
                // finish the output to make docker containers happy
                s.shutdownOutput();
                os.close();
                s.close();
            } catch (IOException e) {
                final String msg = "IOException from ServerThread socket";
                logger.error(msg, e);
                audit.addError(msg, e);
            }
        }
    }
}
