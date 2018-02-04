package com.carnifex.rsyncmover.mover;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.stream.Stream;

public class Permissions {

    private static final Logger logger = LogManager.getLogger();

    private Permissions() {}

    public static void setPermissions(final Path path, final Set<PosixFilePermission> filePermissions, final Set<PosixFilePermission> folderPermissions, final UserPrincipal userPrincipal) {
        try {
            if (Files.isDirectory(path)) {
                Files.setPosixFilePermissions(path, folderPermissions);
            } else {
                Files.setPosixFilePermissions(path, filePermissions);
            }
            if (userPrincipal != null) {
                Files.setOwner(path, userPrincipal);
            }
        } catch (IOException e) {
            logger.warn("Error setting file permissions on " + path, e);
        }
        if (Files.isDirectory(path)) {
            try {
                Files.list(path).forEach(p -> setPermissions(p, filePermissions, folderPermissions, userPrincipal));
            } catch (IOException e) {
                logger.warn("Error setting file permissions on " + path, e);
            }
        }
    }
}
