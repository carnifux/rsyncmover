package com.carnifex.rsyncmover.config;


import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import com.carnifex.rsyncmover.RsyncMover;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import com.carnifex.rsyncmover.notifications.Notifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        try {
            this.watcher = FileSystems.getDefault().newWatchService();
            config.toAbsolutePath().getParent().register(watcher, ENTRY_MODIFY, ENTRY_CREATE);
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
                    final String newConfig = readConfig(Paths.get(configPath));
                    if (!newConfig.equals(previousConfig)) {
                        logger.info("Config change detected, reinitialising");
                        try {
                            RsyncMover.reinit(newConfig);
                            previousConfig = newConfig;
                        } catch (Exception e) {
                            logger.error("Exception loading invalid config", e);
                            logger.error("Reloading previous valid config");
                            RsyncMover.reinit(previousConfig);
                            Notifier.notifiyAll(new ErrorEntry("Exception loading invalid config", e));
                        }
                    }
                } else {
                    logger.debug("Spurious config change detected, ignoring");
                }
            }


            if (!key.reset()) {
                break;
            }
        }
        logger.error("Config watcher exited for some reason");
    }
}
