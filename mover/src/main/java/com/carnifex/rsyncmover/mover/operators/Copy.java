package com.carnifex.rsyncmover.mover.operators;


import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Copy extends MoveOperator {

    @Override
    public void operate(final Path from, final Path to) throws IOException {
        logger.info("Copying " + from + " to " + to);
        if (from.toFile().isDirectory()) {
            FileUtils.copyDirectory(from.toFile(), to.toFile());
        } else {
            Files.copy(from, to);
        }
    }

    @Override
    public String getMethod() {
        return "copy";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }

}
