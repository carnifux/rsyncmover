package com.carnifex.rsyncmover.mover.io;

import com.carnifex.rsyncmover.Utilities;
import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.Type;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import com.carnifex.rsyncmover.audit.entry.NotificationEntry;
import com.carnifex.rsyncmover.sync.SyncedFiles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class FileChangeWatcher extends Thread {

    private static final Logger logger = LogManager.getLogger();
    private static final String DO_NOT_ADD_SUFFIX = ".filenamemovetest";
    private static final int UPDATE_INTERVAL = 10000;

    private final Set<PathHolder> filesToMoveSoon;
    private final Set<Path> dontReAdd;
    private final List<Mover> movers;
    private final boolean isWindows;
    private final MoverThread moverThread;
    private final SyncedFiles syncedFiles;
    private final Audit audit;
    private volatile boolean shutdown;

    public FileChangeWatcher(final List<Mover> movers, final MoverThread moverThread, final SyncedFiles syncedFiles, final Audit audit) {
        super("FileChangeWatcher");
        this.filesToMoveSoon = ConcurrentHashMap.newKeySet();
        this.dontReAdd = ConcurrentHashMap.newKeySet();
        this.isWindows = Utilities.isRunningOnWindows();
        this.movers = movers;
        this.moverThread = moverThread;
        this.syncedFiles = syncedFiles;
        this.audit = audit;
        this.shutdown = false;
        this.start();
        logger.info("File change watcher successfully initialized");
    }

    @Override
    public void run() {
        for (;;) {
            try {
                Thread.sleep(UPDATE_INTERVAL);
                checkFilesToMove();
            } catch (InterruptedException e) {
                logger.debug("Interrupted", e);
                return;
            }
        }
    }

    public void submit(final Path path, final boolean moveImmediately) {
        if (shutdown) {
            return;
        }
        // this class renames the files to find if they're unused, check that we're not adding a file we're renaming
        if (path.toString().endsWith(DO_NOT_ADD_SUFFIX) || dontReAdd.contains(path)) {
            logger.debug("Not adding temporary move file");
            dontReAdd.remove(path);
            return;
        }
        if (!syncedFiles.shouldDownload("file", path.toString())) {
            logger.info("Not re-moving file we've already moved: " + path.toString());
            return;
        }
        if (moveImmediately) {
            logger.info("Immediately moving " + path.toString());
            filesToMoveSoon.add(new PathHolder(path, true));
        }
        logger.info("Registering " + path.toString());
        filesToMoveSoon.add(new PathHolder(path, false));
    }

    private void checkFilesToMove() {
        for (Iterator<PathHolder> iterator = filesToMoveSoon.iterator(); iterator.hasNext(); ) {
            try {
                final PathHolder holder = iterator.next();
                if (holder.isReady()) {
                    final List<Mover> movers = this.movers.stream()
                            .filter(mover -> mover.shouldSubmit(holder.get()))
                            .peek(mover -> logger.debug("Selected possible mover {} for file {}", mover.getName(), holder.get()))
                            .collect(Collectors.toList());
                    if (movers.size() == 0) {
                        logger.error("Unable to find mover for file " + holder.get().toString());
                    } else {
                        final Mover mover = chooseMover(movers);
                        if (mover != null) {
                            final Path target = mover.getTarget(holder.get());
                            moverThread.submit(holder.get(), target, mover);
                            mover.notify(new NotificationEntry(Type.DOWNLOADED, holder.get().getFileName().toString()));
                            syncedFiles.addDownloadedPath("file", holder.get().toString());
                            syncedFiles.finished();
                            dontReAdd.remove(holder.get());
                        } else {
                            final String msg = "Found multiple movers (" +
                                    movers.stream().map(Mover::getName).collect(Collectors.joining(", "))
                                    + ") for file " + holder.get().toString() + "; not moving";
                            logger.error(msg);
                            audit.add(new ErrorEntry(msg));
                        }
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

    private Mover chooseMover(final List<Mover> movers) {
        if (movers.size() == 1) {
            return movers.get(0);
        }
        final List<Mover> priorities = movers.stream().sorted((a, b) -> -Integer.compare(a.getPriority(), b.getPriority())).collect(Collectors.toList());
        // if one has a higher priority than any of the others, then return that, otherwise we can't decide on a mover
        if (priorities.get(0).getPriority() > priorities.get(1).getPriority()) {
            return priorities.get(0);
        }
        return null;
    }

    public void shutdown() {
        this.shutdown = true;
        while (!filesToMoveSoon.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.debug("", e);
                return;
            }
        }
    }

    private final class PathHolder {

        private static final long MIN_INTERVAL = 1000 * 60;

        private final Path path;
        private final File file;
        private long lastSize;
        private long lastModified;
        private long lastPolled;
        private final Set<PathHolder> subDirectories;
        private final boolean isAlwaysReady;

        PathHolder(final Path path, final boolean isAlwaysReady) {
            this.path = path;
            this.file = path.toFile();
            this.lastSize = file.length();
            this.lastModified = file.lastModified();
            this.lastPolled = System.currentTimeMillis();
            this.isAlwaysReady = isAlwaysReady;
            this.subDirectories = this.isAlwaysReady ? Collections.emptySet() : getSubFiles();
        }

        private Set<PathHolder> getSubFiles() {
            if (file.isDirectory()) {
                final File[] files = file.listFiles();
                if (files != null) {
                    return Stream.of(files).map(f -> Paths.get(f.toURI()))
                            .map(p -> new PathHolder(p, false)).collect(Collectors.toSet());
                }
            }
            return Collections.emptySet();
        }

        public boolean isReady() {
            if (isAlwaysReady) {
                return true;
            }
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
                logger.trace(file.getName() + " not changed, " + (overTime ? "time passed, trying to open" : "waiting for time"));
                // try to open the file with write privileges just to be sure
                return overTime && (file.isDirectory() ? tryOpenDirectory(file) : tryOpen(file));
            } else {
                logger.trace(file.getName() + " changed since last check");
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
                logger.trace(f.getName() + " able to be opened");
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
