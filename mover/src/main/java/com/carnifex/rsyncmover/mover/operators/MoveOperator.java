package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.mover.Permissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public abstract class MoveOperator {

    protected static final Logger logger = LogManager.getLogger();
    protected final Audit audit;

    protected abstract Path operate(final Path from, final Path to) throws IOException;
    public abstract String getMethod();
    public abstract boolean shouldSetFilePermissions();

    protected MoveOperator(final Audit audit) {
        this.audit = audit;
    }

    public Path move(final Path from, final Path to, final Set<PosixFilePermission> filePermissions) throws IOException {
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
        final Path path = operate(from, to);
        log(to);
        return path;
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

    public static MoveOperator create(final String value, final List<String> additionalArguments, final Audit audit)  {
        switch (value) {
            case "move":
                return  new Move(audit);
            case "copy":
                return new Copy(audit);
            case "symlink":
                return new Symlink(audit);
            case "move+symlink":
                return new MoveSymlink(audit);
            case "filebot+move":
                return new FileBotMove(audit, additionalArguments);
            case "filebot+move+symlink":
                return new FileBotSymlink(audit, additionalArguments);
            case "filebot+copy":
                return new FileBotCopy(audit, additionalArguments);
            case "filebot":
                return new FileBot(audit, additionalArguments);
            default:
                return new NoOp(audit);
        }
    }
}
