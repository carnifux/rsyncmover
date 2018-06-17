package com.carnifex.rsyncmover.sync;


import com.carnifex.rsyncmover.Utilities;
import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.DownloadedEntry;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import com.carnifex.rsyncmover.audit.entry.SeenEntry;
import com.carnifex.rsyncmover.mover.io.FileWatcher;
import com.carnifex.rsyncmover.mover.io.Mover;
import com.carnifex.rsyncmover.mover.io.MoverThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Syncer extends Thread {

    private static final Logger logger = LogManager.getLogger();

    private final List<String> dlDirs;
    private final SyncedFiles syncedFiles;
    private final List<Sftp> sftps;
    private final int syncFrequency;
    private final boolean passivateEachTime;
    private volatile boolean running;
    private volatile boolean sleeping;
    private volatile boolean syncing;
    private final List<Mover> movers;
    private final long minimumSpace;
    private final Set<PosixFilePermission> filePermissions;
    private final boolean lazyPolling;
    private final List<FileWatcher> fileWatchers;
    private final MoverThread moverThread;
    private final Audit audit;
    private final ExecutorService executorService;
    private final boolean isWindows;

    public Syncer(final List<String> dlDirs, final List<Sftp> sftps, final SyncedFiles syncedFiles, final int syncFrequency,
                  final boolean passivateEachTime, final long minimumSpace, final Set<PosixFilePermission> filePermissions,
                  final boolean downloadsMustMatchMover, final List<Mover> movers, final boolean lazyPolling, final int maxConcurrentDownloads,
                  final boolean runOnce, final List<FileWatcher> fileWatchers, final MoverThread moverThread, final Audit audit) {
        super("Syncer");
        this.dlDirs = dlDirs;
        dlDirs.forEach(dir -> {
            try {
                final Path path = Paths.get(dir);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.syncedFiles = syncedFiles;
        this.sftps = sftps;
        this.sftps.forEach(ssh -> audit.addDownloadWatcher(ssh.getDownloadWatcher()));
        this.syncFrequency = syncFrequency;
        this.passivateEachTime = passivateEachTime;
        this.movers = downloadsMustMatchMover ? movers : Collections.emptyList();
        this.minimumSpace = minimumSpace;
        this.audit = audit;
        this.filePermissions = filePermissions;
        this.lazyPolling = lazyPolling;
        this.fileWatchers = fileWatchers != null ? fileWatchers : Collections.emptyList();
        this.moverThread = moverThread;
        final AtomicInteger threadIndex = new AtomicInteger(0);
        this.executorService = new ThreadPoolExecutor(maxConcurrentDownloads, maxConcurrentDownloads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> new Thread(r, "DownloadThread" + threadIndex.getAndIncrement()));

        this.running = true;
        this.sleeping = false;
        this.syncing = false;
        this.isWindows = Utilities.isRunningOnWindows();
        if (!runOnce) {
            this.start();
        }
    }

    public void sync() {
        if (syncing) {
            return;
        }
        syncing = true;
        boolean downloaded = false;
        for (final Sftp sftp : sftps) {
            try {
                final List<String> allFiles = sftp.listFiles();
                logger.debug(sftp.getServerName() + ": Received following files from sftp: " + allFiles.stream().map(this::normalize).collect(Collectors.joining(", ")));
                final List<String> shouldDownload = allFiles.stream()
                        .peek(file -> audit.add(new SeenEntry(normalize(file), sftp.getServerName())))
                        .filter(file -> syncedFiles.shouldDownload(sftp.getServerName(), normalize(file)))
                        .filter(file -> {
                            final boolean result = this.movers.isEmpty() || this.movers.stream().filter(mover -> mover.shouldSubmit(Paths.get(file))).count() == 1;
                            if (!result) {
                                logger.warn(sftp.getServerName() + ": Not downloading " + file + ", no single mover able to match it");
                            }
                            return result;
                        })
                        .collect(Collectors.toList());

                if (shouldDownload.isEmpty()) {
                    logger.debug(sftp.getServerName() + ": Nothing new to download, finishing");
                } else {
                    logger.info(sftp.getServerName() + ": Downloading following files, as haven't been seen before: " +
                            shouldDownload.stream().map(this::normalize).collect(Collectors.joining(",")));
                    final String dlDir = getDlDir();
                    for (final String fileToDownload : shouldDownload) {
                        try {
                            final long start = System.currentTimeMillis();
                            sftp.downloadFiles(Collections.singletonList(fileToDownload), dlDir, path -> {
                                if (filePermissions != null && !isWindows) {
                                    try {
                                        Files.setPosixFilePermissions(Paths.get(dlDir + File.separator + path), filePermissions);
                                    } catch (Exception e) {
                                        final String msg = sftp.getServerName() + ": Error setting file permissions on downloaded files";
                                        logger.error(msg, e);
                                        audit.add(new ErrorEntry(msg, e));
                                    }
                                }
                                syncedFiles.addDownloadedPath(sftp.getServerName(), normalize(path));
                                audit.add(new DownloadedEntry(path, sftp.getServerName()));

                            }, executorService);
                            logger.info("Finished downloading " + fileToDownload + " in " + (System.currentTimeMillis() - start) / 1000 + "ms");
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            audit.add(new ErrorEntry("Error downloading " + fileToDownload, e));
                        }
                    }
                    downloaded = true;
                }
            } catch (Exception e) {
                final String msg = sftp.getServerName() + ": Exception downloading or listing files";
                logger.error(msg, e);
                audit.add(new ErrorEntry(msg, e));
            }
        }
        logger.debug("Finished downloading new files");
        if (passivateEachTime) {
            syncedFiles.finished();
        }
        if (downloaded && lazyPolling) {
            fileWatchers.forEach(fw -> fw.submitExistingFiles());
        }
        syncing = false;
    }

    private String normalize(final String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD);
    }

    private String getDlDir() {
        return dlDirs.stream()
                .map(Paths::get)
                .filter(path -> {
                    try {
                        return Files.getFileStore(path).getUsableSpace() > minimumSpace;
                    } catch (IOException e) {
                        logger.error("Exception getting free space", e);
                        return false;
                    }
                })
                .map(Path::toString)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Not enough free space to download file"));
    }

    @Override
    public void run() {
        for (;;) {
            try {
                if (!running) {
                    break;
                }
                final long start = System.currentTimeMillis();
                sync();
                sleeping = true;
                // don't sleep for exactly sync frequency if we were downloading for hours, just check again immediately
                Thread.sleep(Math.max(0, (syncFrequency - (System.currentTimeMillis() - start))));
            } catch (InterruptedException e) {
                logger.debug("Interrupted", e);
            }
            sleeping = false;
        }
    }

    @Override
    public void interrupt() {
        if (sleeping && !syncing) {
            super.interrupt();
        }
    }

    public void shutdown() {
        this.running = false;
        syncedFiles.finished();
    }

    public void forceShutdown() {
        final List<Runnable> runnables = executorService.shutdownNow();
        if (!runnables.isEmpty()) {
            logger.warn("Interrupted " + runnables.size() + " download tasks");
        }
    }

}
