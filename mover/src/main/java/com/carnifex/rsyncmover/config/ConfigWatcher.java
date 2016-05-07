package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.RsyncMover;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class ConfigWatcher extends Thread {

    private static final Logger logger = LogManager.getLogger();

    private final String configPath;
    private final WatchService watcher;
    private String previousConfig;


    public ConfigWatcher(final String configPath) {
        super("ConfigWatcher");
        this.configPath = configPath;
        final Path config = Paths.get(configPath);
        this.previousConfig = readConfig(config);
        if (logger.isDebugEnabled()) {
            logger.debug("Previous config: " + previousConfig);
        }
        this.setDaemon(true);
        try {
            this.watcher = FileSystems.getDefault().newWatchService();
            config.getParent().register(watcher, ENTRY_MODIFY, ENTRY_CREATE);
            this.start();
            logger.info("Config watcher initialised");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readConfig(final Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream().reduce("", (a, b) -> a + b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        for (;;) {

            // wait for key to be signaled
            final WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                // should never happen except on shutdown when we dont care
                throw new RuntimeException(e);
            }

            for (final WatchEvent<?> event: key.pollEvents()) {
                final WatchEvent.Kind<?> kind = event.kind();
                logger.trace("WatchEvent triggered");
                if (kind == OVERFLOW) {
                    continue;
                }
                if (configPath.endsWith(File.separator + ((WatchEvent<Path>) event).context().toString())) {
                    logger.info("Config change detected, reinitialising");
                    final String newConfig = readConfig(Paths.get(configPath));
                    if (!newConfig.equals(previousConfig)) {
                        previousConfig = newConfig;
                        RsyncMover.reinit(configPath);
                    }
                }
            }


            if (!key.reset()) {
                break;
            }
        }
        logger.error("Config watcher exited for some reason");
    }
}
