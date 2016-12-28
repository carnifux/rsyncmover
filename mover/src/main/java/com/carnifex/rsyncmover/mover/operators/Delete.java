package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

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
        Files.delete(previousPaths.get(indexToDelete));
        return to;
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
