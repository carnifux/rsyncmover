package com.carnifex.rsyncmover.mover.io;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
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
    private final boolean lazyPolling;
    private final Audit audit;

    public FileWatcher(final String dir, final Set<String> dontWatch, final FileChangeWatcher fileChangeWatcher, final boolean lazyPolling, final Audit audit) {
        super("FileWatcher - " + dir);
        this.dir = Paths.get(dir);
        this.dontWatch = dontWatch;
        this.fileChangeWatcher = fileChangeWatcher;
        this.audit = audit;
        this.lazyPolling = lazyPolling;
        if (!this.dir.toFile().exists() || !this.dir.toFile().canRead()) {
            final String msg = "Watch folder " + dir + " does not exist or is unreadable, will not be watched";
            logger.error(msg);
            audit.add(new ErrorEntry(msg, null));
            watcher = null;
        } else if (lazyPolling) {
            this.watcher = null;
            logger.info("File watcher initialized lazily, watching " + dir);
        } else {
            try {
                this.watcher = FileSystems.getDefault().newWatchService();
                this.dir.register(watcher, ENTRY_CREATE);
                this.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            logger.info("File watcher initialized, watching " + dir);
        }
    }

    @Override
    public void run() {
        submitExistingFiles();
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
                final String msg = "FileWatcher key reset, folder no longer watchable";
                logger.error(msg);
                audit.add(new ErrorEntry(msg, null));
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

    public void submitExistingFiles() {
        // check that any files already existing in the folder need moving
        final File[] files = dir.toFile().listFiles();
        if (files != null && files.length > 0) {
            logger.info("Submitting existing initial files in " + dir);
            for (final File file : files) {
                fileChangeWatcher.submit(file.toPath());
            }
        }
    }
}
