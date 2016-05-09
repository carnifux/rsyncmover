package com.carnifex.rsyncmover.web;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {

    private static final Logger logger = LogManager.getLogger();

    private final ServerSocket socket;
    private final Audit audit;

    public Server(final int port, final Audit audit) {
        super("Server");
        try {
            this.socket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.audit = audit;
        logger.info("Web server successfully initialized on port " + port);
        this.start();
    }

    public void shutdown() {
        try {
            this.interrupt();
            socket.close();
        } catch (IOException e) {
            final String msg = "Exception closing server socket on thread interruption";
            logger.error(msg, e);
            audit.add(new ErrorEntry(msg, e));
        }
    }

    @Override
    public void run() {
        for (;;) {
            try {
                final Socket s = socket.accept();
                new ServerThread(s).start();
            } catch (IOException e) {
                if (this.isInterrupted()) {
                    return;
                }
                final String msg = "IOException from server socket";
                logger.error(msg, e);
                audit.add(new ErrorEntry(msg, e));
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
            final byte[] response = audit.formatAll().getBytes();
            try {
                final OutputStream os = s.getOutputStream();
                os.write(response);
                os.flush();
                // finish the output to make docker containers happy
                s.shutdownOutput();
            } catch (IOException e) {
                final String msg = "IOException from ServerThread socket";
                logger.error(msg, e);
                audit.add(new ErrorEntry(msg, e));
            }
        }
    }
}
