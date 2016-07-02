package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class NoOp extends MoveOperator {

    private NoOp(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
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
