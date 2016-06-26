package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Copy extends MoveOperator {

    public Copy(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
    }

    @Override
    public Path operate(final Path from, final Path to) throws IOException {
        logger.info("Copying " + from + " to " + to);
        if (from.toFile().isDirectory()) {
            FileUtils.copyDirectory(from.toFile(), to.toFile());
        } else {
            Files.copy(from, to);
        }
        return to;
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
