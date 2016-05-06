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

    public void shutdown() {
        try {
            this.client.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getServerName() {
        return server;
    }

    public List<String> listFiles() {
        try {
            client.connect();
            final List<RemoteResourceInfo> ls = client.getSftp().ls(remoteDirectory);
            return ls.stream()
                    .map(RemoteResourceInfo::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.disconnect();
        }
    }

    private String removeSymlink(final String dir) {
        return remoteRealDirectory != null ? dir.replace(remoteDirectory, remoteRealDirectory) : dir;
    }

    public void downloadFiles(final List<String> files, final String target, final Consumer<String> callback) {
        if (files.isEmpty()) {
            return;
        }
        client.connect();
        try (final SFTPClient sftp = client.getSftp()){
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
                if (filePermissions != null) {
                    Files.setPosixFilePermissions(Paths.get(target), filePermissions);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.disconnect();
        }
    }


    private final class SshClient implements AutoCloseable {
        private final SSHClient ssh;

        public SshClient() {
            this.ssh = new SSHClient();
            try {
                init();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public SFTPClient getSftp() {
            try {
                return ssh.newSFTPClient();
            } catch (IOException e) {
                logger.error("Error creating new SFTP client", e);
                throw new RuntimeException(e);
            }
        }

        private void init() throws IOException {
            try {
                ssh.loadKnownHosts();
            } catch (IOException e) {
                logger.warn("Error loading known hosts, continuing");
            }
            if (hostKey != null) {
                ssh.addHostKeyVerifier(hostKey);
            }
        }

        public void connect() {
            try {
                ssh.connect(server, port);
                ssh.authPassword(user, pass);
            } catch (IOException e) {
                final String msg = "Error connecting to " + user + "@" + server + ":" + port;
                logger.error(msg, e);
                throw new RuntimeException(e);
            }
        }

        public void disconnect() {
            try {
                ssh.disconnect();
            } catch (IOException e) {
                final String msg = "Error disconnecting from " + user + "@" + server + ":" + port;
                logger.error(msg, e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws Exception {
            this.ssh.close();
        }
    }

}
