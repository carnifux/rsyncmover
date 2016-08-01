package com.carnifex.rsyncmover.sync;


import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier.Listener;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.TransferListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
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

    public Sftp(final String server, final int port, final String remoteDirectory, final String remoteRealDirectory,
                final String user, final String pass, final String hostKey,
                final Set<PosixFilePermission> filePermissions) {
        this.server = server;
        this.port = port;
        this.remoteDirectory = remoteDirectory;
        this.remoteRealDirectory = remoteRealDirectory;
        this.user = user;
        this.pass = pass;
        this.hostKey = hostKey;
        this.filePermissions = filePermissions;
        this.watcher = new DownloadWatcher();
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

    public void downloadFiles(final List<String> files, final String target, final Consumer<String> callback) {
        if (files.isEmpty()) {
            return;
        }

        try (final SshClient sshClient = new SshClient();
             final SFTPClient sftp = sshClient.getSftp()) {
            for (final String file : files) {
                final String source = remoteDirectory + file;
                try {
                    sftp.get(source, target);
                } catch (Exception e) {
                    if (remoteRealDirectory != null) {
                        logger.warn("Failed downloading file " + file + ", trying symlink dir");
                        try {
                            final String withoutSymlink = removeSymlink(source);
                            sftp.get(withoutSymlink, target);
                        } catch (Exception e1) {
                            logger.error("Failed downloading file " + file + " completely");
                            throw e1;
                        }
                    }
                    logger.error("Failed downloading file " + file);
                    throw e;
                }
                callback.accept(file);
                watcher.finished();
                if (filePermissions != null) {
                    Files.setPosixFilePermissions(Paths.get(target), filePermissions);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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
                } catch (IOException e) {
                    logger.warn("Error loading known hosts, continuing");
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
                logger.error("Error creating new SFTP client", e);
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

        private String name;
        private long size;
        private long transferred;
        private float percent;
        private boolean currentlyActive = false;

        public void reset(final String name, final long size) {
            this.name = name;
            this.size = size;
            this.transferred = 0;
            this.percent = 0f;
            this.currentlyActive = true;
        }

        public void update(final long transferred) {
            this.transferred = transferred;
            this.percent = 100 * ((float) this.transferred / (float) size);
        }

        public String getMessage() {
            return "Downloading " + name + ": " + formatBytes(transferred) + "/" + formatBytes(size) + " - " + percent + "%";
        }

        public String formatBytes(final long bytes) {
            if (bytes > 1000000) { // if > 1mb
                return round(((float) bytes / (float) 1000000), 2) + "MiB";
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

        public void finished() {
            this.currentlyActive = false;
        }

        public String getName() {
            return name;
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
