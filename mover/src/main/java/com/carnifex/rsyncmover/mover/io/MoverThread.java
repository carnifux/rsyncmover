package com.carnifex.rsyncmover.mover.io;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.DuplicateEntry;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import com.carnifex.rsyncmover.audit.entry.MovedEntry;
import com.carnifex.rsyncmover.mover.Permissions;
import com.carnifex.rsyncmover.mover.operators.MoveOperator;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MoverThread extends Thread {

    private static final Logger logger = LogManager.getLogger();

    private final BlockingQueue<PathObject> pathObjectQueue;
    private final AtomicReference<PathObject> currentObject;
    private final Set<PosixFilePermission> filePermissions;
    private final Set<PosixFilePermission> folderPermissions;
    private final UserPrincipal user;
    private final boolean deleteDuplicateFiles;
    private final Audit audit;
    private volatile boolean shutdown;
    private volatile boolean shutdownImmediately;
    private volatile boolean moving;

    public MoverThread(final Set<PosixFilePermission> filePermissions, final Set<PosixFilePermission> folderPermissions,
                       final UserPrincipal user, final boolean deleteDuplicateFiles, final Audit audit) {
        super("MoverThread");
        this.pathObjectQueue = new LinkedBlockingQueue<>();
        this.currentObject = new AtomicReference<>();
        this.filePermissions = filePermissions;
        this.folderPermissions = folderPermissions;
        this.user = user;
        this.deleteDuplicateFiles = deleteDuplicateFiles;
        this.shutdown = false;
        this.moving = false;
        this.audit = audit;
        audit.addMoverThread(this);
        this.start();
        logger.info("MoverThread initialised");
    }

    @Override
    public void run() {
        for (;;) {
            final PathObject poll;
            try {
                if (shutdown && pathObjectQueue.isEmpty()) {
                    return;
                }
                poll = pathObjectQueue.poll(5, TimeUnit.SECONDS);
                if (poll != null) {
                    moving = true;
                    currentObject.set(poll);
                    move(poll);
                    final int remaining = pathObjectQueue.size();
                    if (remaining > 0) {
                        logger.info(remaining + " items to move");
                    } else {
                        logger.info("Finished moving files");
                    }
                    currentObject.set(null);
                }
                if (this.shutdownImmediately) {
                    logger.info("Shutting down MoverThread with " + pathObjectQueue.size() + " items left to be moved");
                    return;
                }
            } catch (InterruptedException e) {
                logger.debug("MoverThread interrupted", e);
                return;
            } finally {
                moving = false;
            }
        }
    }

    public String getCurrentlyMovingObject() {
        if (moving) {
            final PathObject pathObject = currentObject.get();
            if (pathObject != null) {
                return this.getName() + ": " + pathObject.getOperator().getMethod() + ": " + pathObject.getFrom() + " -> " + pathObject.getTo();
            }
        }
        return this.getName() + ": Idle";
    }

    public void shutdown(final boolean immediately) {
        logger.info("Registering shutdown, " + pathObjectQueue.size() + " items left in queue");
        this.shutdown = true;
        if (immediately) {
            this.shutdownImmediately = true;
        }
        if (!pathObjectQueue.isEmpty()) {
            logger.info("Waiting for file move to finish before shutdown");
            while (!pathObjectQueue.isEmpty()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Interrupted whilst waiting for moves to finish on shutdown", e);
                }
            }
        }
        this.interrupt();
    }

    public void submit(final Path from, final Path to, final MoveOperator operator) {
        if (!shutdown) {
            pathObjectQueue.add(new PathObject(from, to, operator));
            logger.info(from.getFileName().toString() + " added to move queue with operator " + operator.getMethod()
                    + "; queue now contains " + pathObjectQueue.size() + " items");
        }
    }

    private void move(final PathObject pathObject) {
        try {
            if (deleteDuplicateFiles && pathObject.getTo().toFile().exists()
                    && Files.size(pathObject.getFrom()) < Files.size(pathObject.getTo())) {
                logger.warn("Deleting duplicate files - " + pathObject.getFrom() + " to " + pathObject.getTo());
                if (pathObject.getTo().toFile().isDirectory()) {
                    FileUtils.deleteDirectory(pathObject.getTo().toFile());
                } else {
                    Files.delete(pathObject.getTo());
                }
                audit.add(new DuplicateEntry(pathObject.getTo().toAbsolutePath().toString()));
            }
            logger.info("Moving " + pathObject.getFrom() + " to " + pathObject.getTo() + " with operator " + pathObject.getOperator().getMethod());
            final Path finalDir = pathObject.getOperator().move(pathObject.getFrom(), pathObject.getTo(), filePermissions, folderPermissions, user);
            logger.info("Move of " + pathObject.getFrom() + " finished; ended up at " + finalDir + ". " + pathObjectQueue.size() + " items remaining");
            audit.add(new MovedEntry(pathObject.getFrom().toAbsolutePath().toString(), finalDir.toAbsolutePath().toString(), pathObject.getOperator().getMethod()));
        } catch (Exception e) {
            final String s = pathObject.getOperator().getMethod() + ": Error moving from " + pathObject.getFrom().toString() + " to " + pathObject.getTo().toString();
            logger.error(s, e);
            audit.add(new ErrorEntry(s, e));
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
