package com.carnifex.rsyncmover.mover.io;


import com.carnifex.rsyncmover.email.Emailer;
import com.carnifex.rsyncmover.mover.Permissions;
import com.carnifex.rsyncmover.mover.operators.MoveOperator;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MoverThread extends Thread {

    private static final Logger logger = LogManager.getLogger(MoverThread.class);

    private final BlockingQueue<PathObject> pathObjectQueue;
    private final Set<PosixFilePermission> filePermissions;
    private final boolean deleteDuplicateFiles;
    private final Emailer emailer;
    private volatile boolean shutdown;

    public MoverThread(final Set<PosixFilePermission> filePermissions, final boolean deleteDuplicateFiles, final Emailer emailer) {
        super("MoverThread");
        this.pathObjectQueue = new LinkedBlockingQueue<>();
        this.filePermissions = filePermissions;
        this.deleteDuplicateFiles = deleteDuplicateFiles;
        this.emailer = emailer;
        this.shutdown = false;
        this.start();
    }

    @Override
    public void run() {
        for (;;) {
            final PathObject poll;
            try {
                poll = pathObjectQueue.poll(1, TimeUnit.DAYS);
                if (poll != null) {
                    move(poll);
                }
            } catch (InterruptedException e) {
                logger.debug("MoverThread interrupted", e);
            }
        }
    }

    public void shutdown() {
        logger.info("Registering shutdown, " + pathObjectQueue.size() + " items left in queue");
        this.shutdown = true;
    }

    public void submit(final Path from, final Path to, final MoveOperator operator) {
        if (!shutdown) {
            pathObjectQueue.add(new PathObject(from, to, operator));
        }
    }

    private void move(final PathObject pathObject) {
        try {
            if (deleteDuplicateFiles && pathObject.getTo().toFile().exists()) {
                logger.warn("Deleting duplicate files");
                if (pathObject.getTo().toFile().isDirectory()) {
                    FileUtils.deleteDirectory(pathObject.getTo().toFile());
                } else {
                    Files.delete(pathObject.getTo());
                }
                emailer.addDuplicateDeletion(pathObject.getTo().toString());
            }
            pathObject.getOperator().move(pathObject.getFrom(), pathObject.getTo(), filePermissions);
            emailer.addMoved(pathObject.getFrom().toString(), pathObject.getTo().toString(), pathObject.getOperator().getMethod());
            if (filePermissions != null && pathObject.getOperator().shouldSetFilePermissions()) {
                Permissions.setPermissions(pathObject.getTo(), filePermissions);
            }
        } catch (Exception e) {
            final String s = "Error moving from " + pathObject.getFrom().toString() + " to " + pathObject.getTo().toString();
            logger.error(s, e);
            emailer.addError(s, e);
        }
    }


    private static final class PathObject {
        private final Path from;
        private final Path to;
        private final MoveOperator operator;

        private PathObject(final Path from, final Path to, final MoveOperator operator) {
            this.from = from;
            this.to = to;
            this.operator = operator;
        }

        private Path getFrom() {
            return from;
        }

        private Path getTo() {
            return to;
        }

        private MoveOperator getOperator() {
            return operator;
        }
    }
}
