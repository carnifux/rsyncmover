package com.carnifex.rsyncmover.mover.operators;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Symlink extends MoveOperator {

    @Override
    protected void operate(final Path from, final Path to) throws IOException {
        logger.info("Creating symlink from " + from + " to " + to);
        Files.createSymbolicLink(to, from);
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
