package com.carnifex.rsyncmover.mover;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Stream;

public class Permissions {

    private static final Logger logger = LogManager.getLogger();

    private Permissions() {}

    public static void setPermissions(final Path path, final Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException e) {
            logger.warn("Error setting file permissions on " + path, e);
        }
        final File file = path.toFile();
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                Stream.of(files).map(File::toPath).forEach(p -> setPermissions(p, permissions));
            }
        }
    }
}
