package com.carnifex.rsyncmover.sync;


import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
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

public class Ssh {

    private static final Logger logger = LogManager.getLogger(Ssh.class);

    private final String server;
    private final int port;
    private final String remoteDirectory;
    private final String remoteRealDirectory;
    private final String user;
    private final String pass;
    private final String hostKey;
    private final Set<PosixFilePermission> filePermissions;
    private final SshClient client;

    public Ssh(final String server, final int port, final String remoteDirectory, final String remoteRealDirectory,
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
        this.client = new SshClient();
        logger.info("Ssh client for server " + server + ":" + port + ", monitoring " + remoteDirectory + " successfully initialized");
    }

    public String getServerName() {
        return server;
    }

    public List<String> listFiles() {
        try {
            final List<RemoteResourceInfo> ls = client.getSftp().ls(remoteDirectory);
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
        try {
            for (final String file : files) {
                final SFTPClient sftp = client.getSftp();
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
                if (filePermissions != null) {
                    Files.setPosixFilePermissions(Paths.get(target), filePermissions);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private final class SshClient implements AutoCloseable {
        private SSHClient ssh;
        private SFTPClient sftp;

        public SshClient() {
            try {
                init();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public SFTPClient getSftp() {
            return this.sftp;
        }

        private void init() throws IOException {
            try {
                this.ssh = new SSHClient();
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
                this.ssh.close();
                throw e;
            }

            try {
                this.sftp = ssh.newSFTPClient();
            } catch (IOException e) {
                this.sftp.close();
                this.ssh.close();
                throw e;
            }
        }

        @Override
        public void close() throws Exception {
            this.sftp.close();
            this.ssh.close();
        }
    }

}
