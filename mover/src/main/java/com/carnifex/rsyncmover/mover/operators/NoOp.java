package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;


public class NoOp extends MoveOperator {

    protected NoOp(final Audit audit) {
        super(audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        logger.error("Using no-op mover for " + from + " to " + to + " - check config");
        return from;
    }

    @Override
    public String getMethod() {
        return "noop";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return false;
    }
}
