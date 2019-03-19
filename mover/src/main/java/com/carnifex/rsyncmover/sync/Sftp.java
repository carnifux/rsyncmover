package com.carnifex.rsyncmover.sync;


import com.carnifex.rsyncmover.Utilities;
import com.carnifex.rsyncmover.audit.TotalDownloaded;
import com.carnifex.rsyncmover.audit.Type;
import com.carnifex.rsyncmover.audit.entry.NotificationEntry;
import com.carnifex.rsyncmover.notifications.Notifier;
import com.google.common.util.concurrent.RateLimiter;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier.Listener;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.TransferListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Sftp {

    private static final Logger logger = LogManager.getLogger();

    private final String server;
    private final int port;
    private final String remoteDirectory;
    private final String remoteRealDirectory;
    private final String user;
    private final String pass;
    private final String hostKey;
    private final Set<PosixFilePermission> filePermissions;
    private final DownloadWatcher watcher;
    private final boolean isWindows;
    private final long maxDownloadSpeed;
    private final List<String> filesInQueue;
    private final List<Notifier> notifiers;
    private final Lock simultaneousLock;
    private final TotalDownloaded totalDownloaded;

    public Sftp(final String server, final int port, final String remoteDirectory, final String remoteRealDirectory,
                final String user, final String pass, final String hostKey,
                final Set<PosixFilePermission> filePermissions, final long maxDownloadSpeed, final List<Notifier> notifiers,
                final Lock simultaneousLock, final TotalDownloaded totalDownloaded) {
        this.server = server;
        this.port = port;
        this.remoteDirectory = remoteDirectory;
        this.remoteRealDirectory = remoteRealDirectory;
        this.user = user;
        this.pass = pass;
        this.hostKey = hostKey;
        this.filePermissions = filePermissions;
        this.watcher = new DownloadWatcher(server, maxDownloadSpeed);
        this.isWindows = Utilities.isRunningOnWindows();
        this.maxDownloadSpeed = maxDownloadSpeed;
        this.filesInQueue = new ArrayList<>();
        this.notifiers = notifiers;
        this.simultaneousLock = simultaneousLock;
        this.totalDownloaded = totalDownloaded;
        logger.info("Sftp client for server " + server + ":" + port + ", monitoring " + remoteDirectory + " successfully initialized");
    }

    public DownloadWatcher getDownloadWatcher() {
        return watcher;
    }

    public String getServerName() {
        return server;
    }

    public List<String> listFiles() {
        try (final SshClient sshClient = new SshClient();
             final SFTPClient sftp = sshClient.getSftp()) {
            final List<RemoteResourceInfo> ls = sftp.ls(remoteDirectory);
            return ls.stream()
                    .map(RemoteResourceInfo::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String removeSymlink(final String dir) {
        return remoteRealDirectory != null ? dir.replace(remoteDirectory, remoteRealDirectory) : dir;
    }

    public void downloadFiles(final List<String> files, final String target, final Consumer<String> callback, final ExecutorService executorService) {
        if (files.isEmpty()) {
            return;
        }
        filesInQueue.addAll(files);

        try (final SshClient sshClient = new SshClient();
             final SFTPClient sftp = sshClient.getSftp()) {
            logger.info(server + ": Downloading " + files.size() + " files");
            for (int i = 0; i < files.size(); i++) {
                final String file = files.get(i);
                final String source = remoteDirectory + file;
                executorService.submit(() -> {
                    if (simultaneousLock != null) {
                        simultaneousLock.lock();
                    }
                    try {
                        logger.info(server + ": Starting download of " + source + " (" + formatSize(getSize(sftp, source)) + ")");
                        final NotificationEntry entry = new NotificationEntry(Type.SEEN, this.getServerName() + "\nStarting download:\n" + file);
                        notifiers.forEach(notifier -> notifier.notify(entry));
                        sftp.get(source, target);
                        return null;
                    } catch (Exception e) {
                        if (remoteRealDirectory != null) {
                            try {
                                logger.warn(server + ": Failed downloading file " + file + ", trying symlink dir");
                                final String withoutSymlink = removeSymlink(source);
                                sftp.get(withoutSymlink, target);
                                return null;
                            } catch (Exception e1) {
                                logger.error(server + ": Failed downloading file " + file + " completely");
                                throw e1;
                            }
                        }
                        logger.error(server + ": Failed downloading file " + file);
                        throw e;
                    } finally {
                        filesInQueue.remove(file);
                        watcher.finished();
                        if (simultaneousLock != null) {
                            simultaneousLock.unlock();
                        }
                    }
                }).get();
                callback.accept(file);
                logger.info(server + ": Finished downloading " + source + "; " + (files.size() - (i + 1)) + " remaining");
                if (filePermissions != null && !isWindows) {
                    Files.setPosixFilePermissions(Paths.get(target), filePermissions);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long getSize(final SFTPClient sftp, final String source) throws IOException {
        final long size = sftp.size(source);
        if (size == 4096) { // is a folder on a linux server
            final List<RemoteResourceInfo> ls = sftp.ls(source);
            return ls.stream().map(rri -> rri.getPath()).map(path -> {
                try {
                    return getSize(sftp, path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).reduce(0L, (a, b) -> a + b);
        }
        return size;
    }

    public List<String> getFilesInQueue() {
        return Collections.unmodifiableList(filesInQueue);
    }

    // visible for testing
    String formatSize(final long size) {
        if (size >= 1_000_000_000) {
            return (size / 1_000_000_000) + "GB";
        } else if (size >= 1_000_000) {
            return (size / 1_000_000) + "MB";
        } else {
            return size + "B";
        }
    }


    private final class SshClient implements AutoCloseable {
        private final SSHClient ssh;
        private SFTPClient client;

        public SshClient() {
            this.ssh = new SSHClient();
            try {
                try {
                    ssh.loadKnownHosts();
                } catch (final IOException e) {
                    logger.trace(server + ": Error loading known hosts, continuing", e);
                }
                if (hostKey != null) {
                    ssh.addHostKeyVerifier(hostKey);
                }
                ssh.connect(server, port);
                ssh.authPassword(user, pass);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public SFTPClient getSftp() {
            try {
                client = ssh.newSFTPClient();
                client.getFileTransfer().setTransferListener(new TransferListener() {
                    @Override
                    public TransferListener directory(final String name) {
                        return this;
                    }

                    @Override
                    public Listener file(final String name, final long size) {
                        watcher.reset(name, size);
                        return watcher::update;
                    }
                });
                return client;
            } catch (IOException e) {
                logger.error(server + ": Error creating new SFTP client", e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws Exception {
            this.ssh.close();
            if (client != null) {
                client.close();
            }
        }
    }

    public class DownloadWatcher {

        private final String name;
        private final long maxDownloadSpeed;
        private String fileName;
        private long size;
        private long started;
        private long transferred;
        private float percent;
        private boolean currentlyActive = false;
        private long speed;
        private final RateLimiter rateLimiter;
        private long lastTime;
        private long lastTransferred;

        public DownloadWatcher(final String name, final long maxDownloadSpeed) {
            this.name = name;
            this.maxDownloadSpeed = maxDownloadSpeed * 1000;
            if (this.maxDownloadSpeed > 0) {
                this.rateLimiter = RateLimiter.create(this.maxDownloadSpeed);
            } else {
                this.rateLimiter = null;
            }
        }

        void reset(final String name, final long size) {
            this.fileName = name;
            this.size = size;
            this.started = System.currentTimeMillis();
            this.lastTime = System.currentTimeMillis();
            this.transferred = 0;
            this.lastTransferred = 0;
            this.percent = 0f;
            this.currentlyActive = true;
            this.speed = 0;
        }

        void update(final long transferred) {
            totalDownloaded.increment(BigInteger.valueOf(transferred));
            final long transferredNow = lastTransferred > transferred ? lastTransferred - transferred : transferred - lastTransferred;
            lastTransferred = transferred;
            if (this.rateLimiter != null) {
                this.rateLimiter.acquire((int) transferredNow);
            }
            final long time = (System.currentTimeMillis() - started) / 1000;
            if (time > 0) {
                this.speed = (long) ((float) transferred / (float) time);
            }
            this.transferred = transferred;
            this.percent = round(100 * ((float) this.transferred / (float) size), 2);
        }

        public String getMessage() {
            return "Downloading " + fileName + ": " + formatBytes(transferred) + "/" + formatBytes(size) + " - " + percent + "% ("
                    + formatBytes(speed) + "/s)";
        }

        private String formatBytes(final long bytes) {
            if (bytes > 1_000_000) { // if > 1mb
                return round(((float) bytes / 1_000_000f), 2) + "MiB";
            }
            if (bytes > 1_000) {
                return round(((float) bytes / 1_000f), 2) + "KiB";
            }
            return bytes + "B";
        }

        private float round(float number, int scale) {
            int pow = 10;
            for (int i = 1; i < scale; i++)
                pow *= 10;
            float tmp = number * pow;
            return (float) (int) ((tmp - (int) tmp) >= 0.5f ? tmp + 1 : tmp) / pow;
        }

        private void finished() {
            this.currentlyActive = false;
        }

        public String getFileName() {
            return fileName;
        }

        public String getName() {
            return this.name;
        }

        public long getSize() {
            return size;
        }

        public long getTransferred() {
            return transferred;
        }

        public float getPercent() {
            return percent;
        }

        public boolean isCurrentlyActive() {
            return currentlyActive;
        }
    }

}
