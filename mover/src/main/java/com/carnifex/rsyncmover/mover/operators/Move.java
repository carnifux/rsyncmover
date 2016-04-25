package com.carnifex.rsyncmover.mover.operators;


import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Move extends MoveOperator {

    @Override
    public void operate(final Path from, final Path to) throws IOException {
        logger.info("Moving " + from + " to " + to);
        if (from.toFile().isDirectory()) {
            FileUtils.moveDirectory(from.toFile(), to.toFile());
        } else {
            Files.move(from, to);
        }
    }

    @Override
    public String getMethod() {
        return "move";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }

}
