package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Symlink extends MoveOperator {

    public Symlink(final Audit audit) {
        super(audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        logger.info("Creating symlink from " + from + " to " + to);
        Files.createSymbolicLink(to, from);
        return to;
    }

    @Override
    public String getMethod() {
        return "symlink";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }

}
