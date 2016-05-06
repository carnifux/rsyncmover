package com.carnifex.rsyncmover.sync;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.mover.io.Mover;
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
import java.util.stream.Collectors;

public class Syncer extends Thread {

    private static final Logger logger = LogManager.getLogger(Syncer.class);

    private final List<String> dlDirs;
    private final SyncedFiles syncedFiles;
    private final List<Ssh> sshs;
    private final int syncFrequency;
    private final boolean passivateEachTime;
    private volatile boolean running;
    private volatile boolean sleeping;
    private final List<Mover> movers;
    private final long minimumSpace;
    private final Set<PosixFilePermission> filePermissions;
    private final Audit audit;

    public Syncer(final List<String> dlDirs, final List<Ssh> sshs, final SyncedFiles syncedFiles, final int syncFrequency,
                  final boolean passivateEachTime, final long minimumSpace, final Set<PosixFilePermission> filePermissions,
                  final boolean downloadsMustMatchMover, final List<Mover> movers, final Audit audit) {
        super("Syncer");
        this.dlDirs = dlDirs;
        this.syncedFiles = syncedFiles;
        this.sshs = sshs;
        this.syncFrequency = syncFrequency;
        this.passivateEachTime = passivateEachTime;
        this.movers = downloadsMustMatchMover ? movers : Collections.emptyList();
        this.minimumSpace = minimumSpace;
        this.audit = audit;
        this.filePermissions = filePermissions;

        this.running = true;
        this.sleeping = false;
        this.start();
    }

    private void sync() {
        for (final Ssh ssh : sshs) {
            try {
                final List<String> allFiles = ssh.listFiles();
                logger.debug("Received following files from ssh: " + allFiles.stream().map(this::normalize).collect(Collectors.joining(", ")));
                final List<String> shouldDownload = allFiles.stream()
                        .peek(file -> audit.addSeen(normalize(file)))
                        .filter(file -> syncedFiles.shouldDownload(ssh.getServerName(), normalize(file)))
                        .filter(file -> {
                            final boolean result = this.movers.isEmpty() || this.movers.stream().filter(mover -> mover.shouldSubmit(Paths.get(file))).count() == 1;
                            if (!result) {
                                logger.warn("Not downloading " + file + ", no single mover able to match it");
                            }
                            return result;
                        })
                        .collect(Collectors.toList());

                if (shouldDownload.isEmpty()) {
                    logger.info("Nothing new to download, finishing");
                } else {
                    logger.info("Downloading following files, as haven't been seen before: " +
                            shouldDownload.stream().map(this::normalize).collect(Collectors.joining(",")));
                    final String dlDir = getDlDir();
                    ssh.downloadFiles(shouldDownload, dlDir, path -> {
                        if (filePermissions != null) {
                            try {
                                Files.setPosixFilePermissions(Paths.get(dlDir + File.separator + path), filePermissions);
                            } catch (Exception e) {
                                final String msg = "Error setting file permissions on downloaded files";
                                logger.error(msg, e);
                                audit.addError(msg, e);
                            }
                        }
                        syncedFiles.addDownloadedPath(ssh.getServerName(), normalize(path));
                        audit.addDownloaded(path);
                    });
                }
            } catch (Exception e) {
                final String msg = "Exception downloading or listing files";
                logger.error(msg, e);
                audit.addError(msg, e);
            }
        }
        logger.debug("Finished downloading new files");
        if (passivateEachTime) {
            syncedFiles.finished();
        }
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
                sync();
                sleeping = true;
                Thread.sleep(syncFrequency);
            } catch (InterruptedException e) {
                logger.debug("Interrupted", e);
            }
            sleeping = false;
        }
    }

    public void shutdown() {
        if (!sleeping) {
            logger.info("Waiting for downloads to finish before shutting down thread");
            while (!sleeping) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Syncer shutdown interrupted whilst waiting for download to finish", e);
                }
            }
            logger.info("Downloads finished, shutting down thread");
        }
        syncedFiles.finished();
        this.running = false;
        if (sleeping) {
            this.interrupt();
        }
    }

}
