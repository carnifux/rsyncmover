package com.carnifex.rsyncmover.mover.io;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class FileChangeWatcher extends Thread {

    private static final Logger logger = LogManager.getLogger(FileChangeWatcher.class);
    private static final String DO_NOT_ADD_SUFFIX = ".filenamemovetest";
    private static final int UPDATE_INTERVAL = 10000;

    private final Set<PathHolder> filesToMoveSoon;
    private final Set<Path> dontReAdd;
    private final List<Mover> movers;
    private final boolean isWindows;
    private final MoverThread moverThread;

    public FileChangeWatcher(final List<Mover> movers, final MoverThread moverThread) {
        super("FileChangeWatcher");
        this.filesToMoveSoon = ConcurrentHashMap.newKeySet();
        this.dontReAdd = ConcurrentHashMap.newKeySet();
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        this.movers = movers;
        this.moverThread = moverThread;
        this.setDaemon(true);
        this.start();
        logger.info("File change watcher successfully initialized");
    }

    @Override
    public void run() {
        for (;;) {
            try {
                Thread.sleep(UPDATE_INTERVAL);
            } catch (InterruptedException e) {
                logger.debug("Interrupted", e);
            }
            checkFilesToMove();
        }
    }

    public void submit(final Path path) {
        // this class renames the files to find if they're unused, check that we're not adding a file we're renaming
        if (path.toString().endsWith(DO_NOT_ADD_SUFFIX) || dontReAdd.contains(path)) {
            logger.debug("Not adding temporary move file");
            dontReAdd.remove(path);
            return;
        }
        logger.info("Registering " + path.toString());
        filesToMoveSoon.add(new PathHolder(path));
    }

    private void checkFilesToMove() {
        for (Iterator<PathHolder> iterator = filesToMoveSoon.iterator(); iterator.hasNext(); ) {
            try {
                final PathHolder holder = iterator.next();
                if (holder.isReady()) {
                    final List<Mover> movers = this.movers.stream()
                            .filter(mover -> mover.shouldSubmit(holder.get()))
                            .collect(Collectors.toList());
                    if (movers.size() == 0) {
                        logger.error("Unable to find mover for file " + holder.get().toString());
                    } else if (movers.size() > 1) {
                        logger.error("Found multiple movers for file " + holder.get().toString() + "; not moving");
                    } else {
                        final Mover mover = movers.get(0);
                        final Path target = mover.getTarget(holder.get());
                        moverThread.submit(holder.get(), target, mover.getMoveOperator());
                    }
                    iterator.remove();
                }
            } catch (NoFileException e) {
                iterator.remove();
                logger.warn("File " + e.getMessage() + " no longer exists, removing");
            } catch (Exception e) {
                // could get exceptions from files being deleted by another process whilst we're watching them
                logger.error("Exception whilst watching file", e);
            }

        }
        logger.trace("Pending files: " + filesToMoveSoon.size());
    }

    private final class PathHolder {

        private static final long MIN_INTERVAL = 1000 * 60;

        private final Path path;
        private final File file;
        private long lastSize;
        private long lastModified;
        private long lastPolled;
        private final Set<PathHolder> subDirectories;

        public PathHolder(final Path path) {
            this.path = path;
            this.file = path.toFile();
            this.lastSize = file.length();
            this.lastModified = file.lastModified();
            this.lastPolled = System.currentTimeMillis();
            this.subDirectories = getSubFiles();
        }

        private Set<PathHolder> getSubFiles() {
            if (file.isDirectory()) {
                final File[] files = file.listFiles();
                if (files != null) {
                    return Stream.of(files).map(f -> Paths.get(f.toURI()))
                            .map(PathHolder::new).collect(Collectors.toSet());
                }
            }
            return Collections.emptySet();
        }

        public boolean isReady() {
            // check that no new files have been added
            if (file.isDirectory()) {
                subDirectories.addAll(getSubFiles());
            }
            return ready() && subDirectories.stream().allMatch(PathHolder::ready);
        }

        private boolean ready() {
            if (!file.exists()) {
                throw new NoFileException(file.toString());
            }
            if (isWindows) {
                // attempting to rename the file is apparently the best way to find out if the file isn't being written to
                // unfortunately this causes the FileWatchers to pick it up as a new file, so we initially rename it with a suffix
                final File renamedFile = new File(path.toString() + DO_NOT_ADD_SUFFIX);
                boolean notLocked = file.renameTo(renamedFile);
                if (notLocked) {
                    logger.trace(file.toString() + " not locked, moving");
                    renamedFile.renameTo(file);
                    // when renaming the file back to the original, store its name temporarily so we can not re-add it
                    dontReAdd.add(path);
                    return true;
                }
                logger.trace(file.toString() + " locked, try again soon");
                return false;
            } else {
                return checkModified();
            }
        }

        private boolean checkModified() {
            // rename trick doesnt work on windows, so poll the last modified and size
            final long thisSize = file.length();
            final long thisModified = file.lastModified();
            final long thisPoll = System.currentTimeMillis();
            if (thisSize == this.lastSize && thisModified == this.lastModified) {
                final boolean overTime = thisPoll > lastPolled + MIN_INTERVAL;
                logger.trace("File not changed, " + (overTime ? "time passed, trying to open" : "waiting for time"));
                // try to open the file with write privileges just to be sure
                return overTime && (file.isDirectory() ? tryOpenDirectory(file) : tryOpen(file));
            }
            this.lastSize = thisSize;
            this.lastModified = thisModified;
            this.lastPolled = thisPoll;
            return false;
        }

        private boolean tryOpenDirectory(final File f) {
            final File[] files = f.listFiles();
            return files != null && Stream.of(files).allMatch(file -> file.isDirectory() ? tryOpenDirectory(file) : tryOpen(file));
        }

        private boolean tryOpen(final File f) {
            RandomAccessFile tryFile = null;
            try {
                tryFile = new RandomAccessFile(f, "rw");
                logger.trace("File able to be opened, moving");
                return true;
            } catch (IOException e) {
                logger.debug(e);
                return false;
            } finally {
                if (tryFile != null) {
                    try {
                        tryFile.close();
                    } catch (IOException ignore) {}
                }
            }
        }


        public Path get() {
            return path;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final PathHolder holder = (PathHolder) o;

            return path.equals(holder.path);

        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }

    private static final class NoFileException extends RuntimeException {
        public NoFileException(final String message) {
            super(message);
        }
    }
}