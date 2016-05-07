package com.carnifex.rsyncmover.mover.io;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileWatcher extends Thread {

    private static final Logger logger = LogManager.getLogger();

    private final Path dir;
    private final WatchService watcher;
    private final Set<String> dontWatch;
    private final FileChangeWatcher fileChangeWatcher;

    public FileWatcher(final String dir, final Set<String> dontWatch, final FileChangeWatcher fileChangeWatcher) {
        super("FileWatcher - " + dir);
        this.dir = Paths.get(dir);
        this.dontWatch = dontWatch;
        this.fileChangeWatcher = fileChangeWatcher;
        if (!this.dir.toFile().exists() || !this.dir.toFile().canRead()) {
            logger.error("Watch folder " + dir + " does not exist or is unreadable, will not be watched");
            watcher = null;
        } else {
            try {
                this.watcher = FileSystems.getDefault().newWatchService();
                this.dir.register(watcher, ENTRY_CREATE);
                this.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        logger.info("File watcher initialized, watching " + dir);
    }

    @Override
    public void run() {
        initialMove();
        for (;;) {

            // wait for key to be signaled
            final WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                // should never happen except on shutdown when we dont care
                logger.debug("Interrupted exception", e);
                return;
            }

            for (final WatchEvent<?> event: key.pollEvents()) {
                final WatchEvent.Kind<?> kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                final WatchEvent<Path> ev = (WatchEvent<Path>)event;
                final Path filename = ev.context();

                final Path absolutePath = toAbsolute(filename);
                if (!dontWatch.contains(absolutePath.toString())) {
                    fileChangeWatcher.submit(absolutePath);
                }
            }


            if (!key.reset()) {
                break;
            }
        }
    }


    public void shutdown() {
        this.interrupt();
    }

    // horrible dirty hack to get around having relative paths from the watch service
    private Path toAbsolute(final Path filename) {
        final String newPath = dir.toString() + File.separator + filename.toString();
        try {
            return Paths.get(newPath);
        } catch (Exception e) {
            return filename;
        }

    }

    private void initialMove() {
        // check that any files already existing in the folder need moving
        final File[] files = dir.toFile().listFiles();
        if (files != null && files.length > 0) {
            logger.info("Submitting pre-existing initial files in " + dir);
            for (final File file : files) {
                fileChangeWatcher.submit(file.toPath());
            }
        }
    }
}
