package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Move extends MoveOperator {

    private Move(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
    }

    @Override
    public Path operate(final Path from, final Path to) throws IOException {
        logger.info("Moving " + from + " to " + to);
        if (from.toFile().isDirectory()) {
            FileUtils.moveDirectory(from.toFile(), to.toFile());
        } else {
            Files.move(from, to);
        }
        return to;
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
