package com.carnifex.rsyncmover.mover.operators;

import java.io.IOException;
import java.nio.file.Path;


public class NoOp extends MoveOperator {

    @Override
    protected void operate(final Path from, final Path to) throws IOException {
        logger.error("Using no-op mover for " + from + " to " + to + " - check config");
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
