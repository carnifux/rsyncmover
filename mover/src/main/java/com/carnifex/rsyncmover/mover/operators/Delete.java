package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Delete extends MoveOperator implements StatefulOperator {

    private final int indexToDelete;

    public Delete() {
        this.indexToDelete = -1;
    }

    public Delete(Audit audit, List<String> additionalArguments) {
        super(audit, additionalArguments);
        if (additionalArguments == null || additionalArguments.size() != 1) {
            throw new RuntimeException("Delete operator must have a single integer additional argument to represent the " +
                "index of the file (in terms of operators used) that will be deleted");
        }
        this.indexToDelete = Integer.valueOf(additionalArguments.get(0));
    }

    @Override
    public Path operateStatefully(final Path from, final Path to, final List<Path> previousPaths) throws IOException {
        final Path path = previousPaths.get(indexToDelete);
        try {
            logger.info("Deleting " + path);
            delete(path);
        } catch (IOException e) {
            final String msg = "Error deleting files; continuing";
            logger.error(msg, e);
            audit.add(new ErrorEntry(msg, e));
        }
        return to;
    }

    private void delete(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.list(path).forEach(p -> {
                try {
                    delete(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        if (Files.exists(path)) {
            logger.trace("Deleting " + path);
            Files.delete(path);
        }
    }

    @Override
    protected Path operate(Path from, Path to) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMethod() {
        return "delete";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return false;
    }
}
