package com.carnifex.rsyncmover.sync;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SyncedFiles {

    private static final Logger logger = LogManager.getLogger();
    private static final String SERVER_SEPARATOR = ":::";

    private volatile Map<String, Set<String>> synced;
    private final Path persistLocation;
    private volatile boolean passive;
    private final boolean active;

    public SyncedFiles(final Path persistLocation) {
        this.synced = new ConcurrentHashMap<>();
        this.persistLocation = persistLocation;
        if (persistLocation != null) {
            final File file = persistLocation.toFile();
            if (!file.exists()) {
                boolean create = false;
                try {
                    create = file.createNewFile();
                } catch (IOException ignore) {}
                if (!create) {
                    logger.error("Could not create persist file " + persistLocation.toString() + ", will not function");
                    this.active = false;
                    this.passive = false;
                } else {
                    this.active = true;
                    this.passive = true;
                }
            } else {
                this.active = true;
                this.passive = true;
            }
        } else {
            logger.error("Could not create persist file, will not function");
            this.active = false;
            this.passive = false;
        }

    }

    public boolean shouldDownload(final String serverName, final String path) {
        if (!active) {
            return true;
        }
        depersist();
        return !synced.getOrDefault(serverName, Collections.emptySet()).contains(path);
    }

    public void addDownloadedPath(final String serverName, final String path) {
        if (!active) {
            return;
        }
        depersist();
        synced.computeIfAbsent(serverName, ignore -> new HashSet<>()).add(path);
    }

    public void finished() {
        persist();
    }

    private void persist() {
        if (!passive && active) {
            synchronized (this) {
                if (!passive) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Persisting " + synced.values().stream().mapToInt(Set::size).sum() + " entries");
                    }
                    final String collect = synced.entrySet().stream()
                            .flatMap(entry -> entry.getValue().stream().map(set -> entry.getKey() + SERVER_SEPARATOR + set))
                            .sorted().collect(Collectors.joining("\n"));
                    try {
                        Files.write(persistLocation, collect.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (Exception e) {
                        logger.error("Exception encountered persisting downloaded files", e);
                    }
                    passive = true;
                }
            }
        }
    }

    private void depersist() {
        if (passive && active) {
            synchronized (this) {
                if (passive) {
                    try {
                        final Map<String, Set<String>> newSynced = new ConcurrentHashMap<>();
                        final List<String> entries = Files.readAllLines(persistLocation);
                        logger.debug("Depersisted " + entries.size() + " entries");
                        entries.stream().forEach(string -> {
                            final String[] split = string.split(SERVER_SEPARATOR);
                            newSynced.computeIfAbsent(split[0], ignore -> new HashSet<>()).add(split[1]);
                        });
                        synced = newSynced;
                    } catch (Exception e) {
                        logger.error("Exception encountered depersisting downloaded files", e);
                    }
                    passive = false;
                }
            }
        }
    }
}
