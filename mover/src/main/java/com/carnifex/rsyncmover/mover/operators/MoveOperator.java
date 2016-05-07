package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.mover.Permissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.Set;

public abstract class MoveOperator {

    protected static final Logger logger = LogManager.getLogger();

    protected abstract void operate(final Path from, final Path to) throws IOException;
    public abstract String getMethod();
    public abstract boolean shouldSetFilePermissions();

    public void move(final Path from, final Path to, final Set<PosixFilePermission> filePermissions) throws IOException {
        if (from.equals(to)) {
            final String msg = "Origin and destination are the same, not moving: " + to;
            logger.info(msg);
            throw new IOException(msg);
        }
        if (to.toFile().exists()) {
            final String msg = "File already exists, not moving: " + to;
            logger.error(msg);
            throw new IOException(msg);
        }

        final Path parent = to.getParent();
        final boolean mkdirs = parent.toFile().mkdirs();
        if (mkdirs) {
            logger.debug("Created directories for move " + parent.toString());
            if (filePermissions != null) {
                Permissions.setPermissions(parent, filePermissions);
            }
        }
        operate(from, to);
        log(to);
    }


    private void log(final Path path) {
        logger.info("Finished moving " + path);
        final String message = LocalDateTime.now().toString() + " " + path.getFileName() + "\n";
        try {
            Files.write(path.getParent().resolve("rsync_mover.log"), message.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            logger.error("Error writing log for " + path.toString(), e);
        }
    }

    public static MoveOperator create(final String value)  {
        switch (value) {
            case "move":
                return new Move();
            case "copy":
                return new Copy();
            case "symlink":
                return new Symlink();
            case "move+symlink":
                return new MoveSymlink();
            default:
                return new NoOp();
        }
    }

}
